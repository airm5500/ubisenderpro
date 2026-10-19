import { TooltipModule } from 'primeng/tooltip';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { DestinataireMasse, EnvoiMasse, EnvoiMasseApercu, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

interface ListeCourte { id: number; nom: string; statut: string; }

/** Un meme message WhatsApp a des numeros colles ou aux clients d'une liste, depuis son numero, au debit des relances. */
@Component({
  selector: 'app-envoi-masse',
  imports: [FormsModule, JourPipe, TelephonePipe, TooltipModule],
  templateUrl: './envoi-masse.html'
})
export class EnvoiMassePage implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  protected readonly envois = signal<Page<EnvoiMasse> | null>(null);
  protected readonly listes = signal<ListeCourte[]>([]);
  protected readonly saisie = signal(false);
  protected readonly apercu = signal<EnvoiMasseApercu | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly detail = signal<EnvoiMasse | null>(null);
  protected readonly destinataires = signal<Page<DestinataireMasse> | null>(null);
  formulaire = vide();
  filtreStatut = '';

  ngOnInit(): void {
    this.charger();
    if (this.auth.peut('LISTES')) {
      this.api.get<ListeCourte[]>('/api/marketing/listes').subscribe(l => this.listes.set(l));
    }
  }

  nouveau(): void {
    this.formulaire = vide();
    this.apercu.set(null);
    this.erreur.set(null);
    this.detail.set(null);
    this.saisie.set(true);
  }

  /** Toute modification oblige a refaire l'apercu avant d'envoyer. */
  modifie(): void {
    this.apercu.set(null);
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
    this.api.postSilencieux<EnvoiMasseApercu>('/api/envois-masse', { ...f, listeId: f.listeId, apercu }).subscribe({
      next: r => {
        this.enCours.set(false);
        if (apercu) {
          this.apercu.set(r);
          return;
        }
        this.messages.add(r.horsPlage
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
  return { nom: '', contenu: 'Bonjour {nom}, ', numeros: '', listeId: null as number | null };
}
