import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { Component, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { DestinataireMasse, EnvoiMasse, EnvoiMasseApercu, ListeDiffusion, ModeleMessage, Page, PieceJointe, Variable } from '../../core/modeles';
import { ClientChoisi, SelecteurClients } from '../../core/selecteur-clients';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';
import { EnvoyerMaintenant } from '../../core/envoyer-maintenant';

interface References { listes: ListeDiffusion[]; modeles: ModeleMessage[]; variables: Variable[]; }
interface LectureFichier { valides: DestinataireMasse[]; invalides: DestinataireMasse[]; }

/**
 * Un meme message WhatsApp, parti d'un modele ou ecrit a la main, avec ses variables et ses pieces jointes, envoye depuis
 * son numero a des clients choisis, a une liste de diffusion, a un fichier importe ou a des numeros colles (repris
 * d'UbiSenderPro), au debit des relances.
 */
@Component({
  selector: 'app-envoi-masse',
  imports: [FormsModule, DialogModule, JourPipe, TelephonePipe, TooltipModule, SelecteurClients, EnvoyerMaintenant],
  templateUrl: './envoi-masse.html'
})
export class EnvoiMassePage implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  protected readonly envois = signal<Page<EnvoiMasse> | null>(null);
  protected readonly references = signal<References>({ listes: [], modeles: [], variables: [] });
  protected readonly groupesModeles = computed(() => {
    const groupes = new Map<string, ModeleMessage[]>();
    for (const m of this.references().modeles) {
      groupes.set(m.typeCampagne, [...(groupes.get(m.typeCampagne) ?? []), m]);
    }
    return [...groupes.entries()].map(([type, modeles]) => ({ type, modeles }));
  });
  protected readonly zone = viewChild<ElementRef<HTMLTextAreaElement>>('zone');
  protected readonly selecteur = signal(false);
  protected readonly clients = signal<ClientChoisi[]>([]);
  protected readonly pieces = signal<PieceJointe[]>([]);
  protected readonly depot = signal(false);
  protected readonly aCorriger = signal<DestinataireMasse[]>([]);
  protected programme = false;
  protected readonly saisie = signal(false);
  protected readonly apercu = signal<EnvoiMasseApercu | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly detail = signal<EnvoiMasse | null>(null);
  protected readonly destinataires = signal<Page<DestinataireMasse> | null>(null);
  /** Avancement de l'envoi ouvert : messages traites (envoyes ou en echec) sur le total a envoyer. */
  protected readonly progression = computed(() => {
    const e = this.detail();
    if (!e) return null;
    const faits = e.nbEnvoyes + e.nbEchecs;
    const total = faits + e.nbEnAttente;
    return { faits, total, pourcent: total ? Math.round(faits * 100 / total) : 100 };
  });
  formulaire = vide();
  filtreStatut = '';

  constructor() {
    // Tant que l'envoi ouvert est en cours, ses compteurs se mettent a jour tout seuls
    const minuteur = setInterval(() => this.suivreAvancement(), 4000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuteur));
  }

  private suivreAvancement(): void {
    const e = this.detail();
    if (!e || e.statut !== 'EN_COURS' || !e.nbEnAttente || document.hidden) return;
    this.api.getSilencieux<EnvoiMasse>(`/api/envois-masse/${e.id}`).subscribe(d => {
      if (this.detail()?.id !== d.id) return;
      this.detail.set(d);
      if (!d.nbEnAttente) this.chargerDestinataires();
    });
  }

  ngOnInit(): void {
    this.charger();
    this.api.get<References>('/api/envois-masse/references').subscribe(r => this.references.set(r));
  }

  nouveau(): void {
    this.formulaire = vide();
    this.clients.set([]);
    this.pieces.set([]);
    this.aCorriger.set([]);
    this.programme = false;
    this.apercu.set(null);
    this.erreur.set(null);
    this.detail.set(null);
    this.saisie.set(true);
  }

  /** Toute modification oblige a refaire l'apercu avant d'envoyer. */
  modifie(): void {
    this.apercu.set(null);
  }

  /** Le texte du modele remplace le message, qui reste modifiable. */
  choisirModele(id: number | null): void {
    this.formulaire.modeleId = id;
    const m = this.references().modeles.find(x => x.id === id);
    if (m) this.formulaire.contenu = m.contenu;
    this.modifie();
  }

  inserer(code: string): void {
    const zone = this.zone()?.nativeElement;
    const texte = this.formulaire.contenu;
    const debut = zone?.selectionStart ?? texte.length;
    const fin = zone?.selectionEnd ?? texte.length;
    this.formulaire.contenu = texte.substring(0, debut) + `{${code}}` + texte.substring(fin);
    this.modifie();
    setTimeout(() => { zone?.focus(); zone?.setSelectionRange(debut + code.length + 2, debut + code.length + 2); });
  }

  ajouterClients(choisis: ClientChoisi[]): void {
    this.clients.set(choisis);
    this.modifie();
  }

  retirerClient(id: number): void {
    this.clients.update(l => l.filter(c => c.id !== id));
    this.modifie();
  }

  deposer(champ: HTMLInputElement): void {
    const fichiers = [...(champ.files ?? [])];
    champ.value = '';
    for (const fichier of fichiers) {
      this.depot.set(true);
      this.api.envoyerFichier<PieceJointe>('/api/envois-masse/pieces-jointes', fichier).subscribe({
        next: p => { this.depot.set(false); this.pieces.update(l => [...l, p]); this.modifie(); },
        error: () => this.depot.set(false)
      });
    }
  }

  retirerPiece(id: number): void {
    this.pieces.update(l => l.filter(p => p.id !== id));
    this.modifie();
  }

  /** Lignes reconnues ajoutees aux numeros ; les autres restent a corriger avant d'etre reintegrees. */
  importer(champ: HTMLInputElement): void {
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) return;
    this.api.envoyerFichier<LectureFichier>('/api/envois-masse/lecture-fichier', fichier).subscribe(r => {
      this.ajouterLignes(r.valides);
      this.aCorriger.set(r.invalides.map(d => ({ ...d })));
      this.messages.add({ severity: r.invalides.length ? 'warn' : 'success', summary: `${r.valides.length} numéro(s) ajouté(s)`,
        detail: r.invalides.length ? `${r.invalides.length} ligne(s) à corriger plus bas.` : undefined });
    });
  }

  reintegrer(): void {
    const corriges = this.aCorriger().filter(d => (d.saisi ?? '').trim());
    this.ajouterLignes(corriges);
    this.aCorriger.set(this.aCorriger().filter(d => !(d.saisi ?? '').trim()));
  }

  private ajouterLignes(lignes: DestinataireMasse[]): void {
    if (!lignes.length) return;
    const texte = lignes.map(d => (d.numero ?? d.saisi ?? '').trim() + (d.nom ? ';' + d.nom : '')).join('\n');
    this.formulaire.numeros = (this.formulaire.numeros.trim() ? this.formulaire.numeros.trimEnd() + '\n' : '') + texte;
    this.modifie();
  }

  aDesDestinataires(): boolean {
    return !!this.formulaire.numeros.trim() || !!this.formulaire.listeId || this.clients().length > 0;
  }

  relancerEchecs(e: EnvoiMasse): void {
    this.api.post<EnvoiMasse>(`/api/envois-masse/${e.id}/relance-echecs`).subscribe(d => {
      this.messages.add({ severity: 'success', summary: 'Échecs remis en attente', detail: 'Ils repartiront au rythme des relances.' });
      this.detail.set(d);
      this.chargerDestinataires();
      this.charger();
    });
  }

  verifier(): void {
    this.soumettre(true);
  }

  envoyer(): void {
    this.soumettre(false);
  }

  ouvrir(e: EnvoiMasse): void {
    this.saisie.set(false);
    this.detail.set(e);
    this.filtreStatut = '';
    this.chargerDestinataires();
  }

  chargerDestinataires(): void {
    const e = this.detail();
    if (!e) return;
    this.api.get<EnvoiMasse>(`/api/envois-masse/${e.id}`).subscribe(d => this.detail.set(d));
    this.api.get<Page<DestinataireMasse>>(`/api/envois-masse/${e.id}/destinataires`, { statut: this.filtreStatut || null, page: 0, taille: 200 })
      .subscribe(p => this.destinataires.set(p));
  }

  annuler(e: EnvoiMasse): void {
    this.api.post<EnvoiMasse>(`/api/envois-masse/${e.id}/annulation`).subscribe(d => {
      this.messages.add({ severity: 'info', summary: 'Envoi annulé', detail: `${d.nbEnAttente === 0 ? 'Plus aucun message ne partira.' : ''}` });
      this.detail.set(d);
      this.chargerDestinataires();
      this.charger();
    });
  }

  private soumettre(apercu: boolean): void {
    const f = this.formulaire;
    this.enCours.set(true);
    this.erreur.set(null);
    const corps = { ...f, clientIds: this.clients().map(c => c.id), pieceJointeIds: this.pieces().map(p => p.id),
      departLe: this.programme && f.departLe ? f.departLe : null, apercu };
    this.api.postSilencieux<EnvoiMasseApercu>('/api/envois-masse', corps).subscribe({
      next: r => {
        this.enCours.set(false);
        if (apercu) {
          this.apercu.set(r);
          return;
        }
        this.messages.add(corps.departLe
          ? { severity: 'info', summary: 'Envoi programmé', life: 10000, detail: `Les ${r.nbRetenus} message(s) partiront à partir du ${corps.departLe.replace('T', ' à ')} (heure d'Abidjan).` }
          : r.horsPlage
          ? { severity: 'info', summary: 'Envoi programmé', life: 10000,
              detail: `Il est hors de la plage des envois en masse : les ${r.nbRetenus} message(s) partiront depuis votre numéro à son ouverture (entre ${r.plageHoraire}, heure d'Abidjan), après les campagnes.` }
          : { severity: 'success', summary: 'Envoi lancé', detail: `${r.nbRetenus} message(s) partiront depuis votre numéro, au rythme des relances.` });
        this.saisie.set(false);
        this.charger();
        if (r.envoi) this.ouvrir(r.envoi);
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Envoi impossible.'); }
    });
  }

  private charger(): void {
    this.api.get<Page<EnvoiMasse>>('/api/envois-masse', { page: 0, taille: 50 }).subscribe(p => this.envois.set(p));
  }
}

function vide() {
  return { nom: '', contenu: 'Bonjour {nom}, ', numeros: '', listeId: null as number | null, modeleId: null as number | null, departLe: '' };
}
