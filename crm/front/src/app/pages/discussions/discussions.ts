import { TooltipModule } from 'primeng/tooltip';
import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Client, Discussion, MessageDiscussion, Page, ModeleMessage, PieceJointe } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

/** Echanges WhatsApp avec les clients : relances, promotions et envois partis, messages recus, reponses ecrites ici. */
@Component({
  selector: 'app-discussions',
  imports: [FormsModule, RouterLink, JourPipe, TelephonePipe, TooltipModule],
  templateUrl: './discussions.html',
  styleUrl: './discussions.css'
})
export class Discussions implements OnInit {
  private readonly api = inject(Api);
  protected readonly L = LIBELLES;
  private readonly fil = viewChild<ElementRef<HTMLDivElement>>('fil');

  q = '';
  texte = '';
  protected readonly page = signal<Page<Discussion> | null>(null);
  protected readonly choisie = signal<Discussion | null>(null);
  protected readonly messages = signal<MessageDiscussion[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly titre = computed(() => {
    const d = this.choisie();
    return d ? d.clientNom ?? d.nomAffiche ?? d.numeroClient : '';
  });

  protected readonly redaction = signal(false);
  protected readonly numeros = signal<string[]>([]);
  protected readonly clientsTrouves = signal<Client[]>([]);
  protected readonly erreurRedac = signal<string | null>(null);
  qClient = '';
  redac = { numeroAgent: '', numeroClient: '', contenu: '' };
  private minuteur: ReturnType<typeof setTimeout> | undefined;
  /** Modeles proposes a l'ecriture ; leurs variables se remplissent avec la fiche du client a l'envoi. */
  protected readonly modeles = signal<ModeleMessage[]>([]);
  protected readonly piecesRedac = signal<PieceJointe[]>([]);
  protected readonly piecesReponse = signal<PieceJointe[]>([]);
  protected readonly depot = signal(false);

  ngOnInit(): void {
    this.rechercher();
    this.api.get<ModeleMessage[]>('/api/discussions/modeles').subscribe(m => this.modeles.set(m));
  }

  /** Ecrire a un client depuis son numero (ou celui de l'agent remplace). */
  nouvelle(): void {
    this.redac = { numeroAgent: '', numeroClient: '', contenu: '' };
    this.piecesRedac.set([]);
    this.qClient = '';
    this.clientsTrouves.set([]);
    this.erreurRedac.set(null);
    this.redaction.set(true);
    this.api.get<string[]>('/api/discussions/numeros').subscribe(n => {
      this.numeros.set(n);
      this.redac.numeroAgent = n[0] ?? '';
    });
  }

  chercherClients(): void {
    clearTimeout(this.minuteur);
    const q = this.qClient.trim();
    if (q.length < 2) { this.clientsTrouves.set([]); return; }
    this.minuteur = setTimeout(() => this.api.get<Page<Client>>('/api/clients', { q, page: 0, taille: 6 })
      .subscribe(p => this.clientsTrouves.set(p.elements)), 250);
  }

  numerosClient(c: Client): string[] {
    return [...new Set([c.numeroPrincipal, c.numeroSecondaire, ...Object.values(c.numeros ?? {})].filter((n): n is string => !!n))];
  }

  choisirClient(c: Client, numero: string): void {
    this.redac.numeroClient = numero;
    this.qClient = c.raisonSociale;
    this.clientsTrouves.set([]);
  }

  demarrer(): void {
    this.enCours.set(true);
    this.erreurRedac.set(null);
    this.api.postSilencieux<MessageDiscussion>('/api/discussions', { ...this.redac, pieceJointeIds: this.piecesRedac().map(p => p.id) }).subscribe({
      next: () => {
        this.piecesRedac.set([]);
        this.enCours.set(false);
        this.redaction.set(false);
        this.choisie.set(null);
        this.rechercher();
      },
      error: e => { this.enCours.set(false); this.erreurRedac.set(e?.error?.message ?? "Le message n'est pas parti."); }
    });
  }

  rechercher(): void {
    this.api.get<Page<Discussion>>('/api/discussions', { q: this.q || null, page: 0, taille: 100 }).subscribe(p => {
      this.page.set(p);
      const c = this.choisie();
      if (!c && p.elements.length) this.ouvrir(p.elements[0]);
    });
  }

  ouvrir(d: Discussion): void {
    this.choisie.set(d);
    this.texte = '';
    this.erreur.set(null);
    this.chargerFil();
  }

  envoyer(): void {
    const d = this.choisie();
    if (!d || !this.texte.trim()) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<MessageDiscussion>('/api/discussions/messages', { numeroAgent: d.numeroAgent, numeroClient: d.numeroClient, contenu: this.texte,
      pieceJointeIds: this.piecesReponse().map(p => p.id) })
      .subscribe({
        next: () => { this.enCours.set(false); this.texte = ''; this.piecesReponse.set([]); this.chargerFil(); },
        error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? "Le message n'est pas parti."); this.chargerFil(); }
      });
  }

  /** Texte du modele choisi, a la place du texte en cours. */
  modeleTexte(id: string): string {
    return this.modeles().find(m => m.id === +id)?.contenu ?? '';
  }

  deposer(champ: HTMLInputElement, cible: 'redac' | 'reponse'): void {
    const fichiers = [...(champ.files ?? [])];
    champ.value = '';
    const liste = cible === 'redac' ? this.piecesRedac : this.piecesReponse;
    for (const fichier of fichiers) {
      this.depot.set(true);
      this.api.envoyerFichier<PieceJointe>('/api/discussions/pieces-jointes', fichier).subscribe({
        next: p => { this.depot.set(false); liste.update(l => [...l, p]); },
        error: () => this.depot.set(false)
      });
    }
  }

  retirer(id: number, cible: 'redac' | 'reponse'): void {
    (cible === 'redac' ? this.piecesRedac : this.piecesReponse).update(l => l.filter(p => p.id !== id));
  }

  private chargerFil(): void {
    const d = this.choisie();
    if (!d) return;
    this.api.get<MessageDiscussion[]>('/api/discussions/messages', { numeroAgent: d.numeroAgent, numeroClient: d.numeroClient }).subscribe(m => {
      this.messages.set(m);
      setTimeout(() => { const f = this.fil()?.nativeElement; if (f) f.scrollTop = f.scrollHeight; });
    });
  }
}
