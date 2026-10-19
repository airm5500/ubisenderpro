import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { JourPipe } from '../../core/format';
import { SupportErreur, SupportFaq, SupportIntervenant, SupportSonde, SupportTicket } from '../../core/modeles';

type Onglet = 'mes' | 'tous' | 'diagnostic' | 'sante' | 'faq';

const STATUTS: Record<string, string> = { OUVERT: 'Ouvert', EN_COURS: 'En cours', RESOLU: 'Résolu', FERME: 'Fermé' };
const CATEGORIES: Record<string, string> = { QUESTION: 'Question', INCIDENT: 'Incident', DEMANDE: 'Demande' };
const PRIORITES: Record<string, string> = { NORMALE: 'Normale', HAUTE: 'Haute', URGENTE: 'Urgente' };

interface EditionTicket { sujet: string; categorie: string; priorite: string; message: string; }
interface EditionFaq { categorie: string; question: string; reponse: string; ordre: number | null; }

/**
 * Centre de support : chacun ouvre et suit ses tickets et lit la FAQ ; le titulaire du droit Support traite tous les tickets,
 * consulte le diagnostic des erreurs et la sante de l'application, et tient la FAQ. Rien ne se supprime.
 */
@Component({
  selector: 'app-support',
  imports: [TooltipModule, FormsModule, DialogModule, JourPipe],
  templateUrl: './support.html'
})
export class Support implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);

  protected readonly statuts = STATUTS;
  protected readonly categories = CATEGORIES;
  protected readonly priorites = PRIORITES;
  protected readonly codesStatut = Object.keys(STATUTS);

  protected readonly support = computed(() => this.auth.peut('SUPPORT'));
  protected readonly onglet = signal<Onglet>('mes');

  // Tickets
  protected readonly tickets = signal<SupportTicket[] | null>(null);
  protected readonly filtreStatut = signal('');
  protected readonly ticket = signal<SupportTicket | null>(null);
  protected readonly intervenants = signal<SupportIntervenant[]>([]);
  protected readonly nouveauOuvert = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  edition: EditionTicket = { sujet: '', categorie: 'QUESTION', priorite: 'NORMALE', message: '' };
  reponse = '';

  // Diagnostic
  protected readonly erreurs = signal<SupportErreur[] | null>(null);
  protected readonly traitees = signal(false);
  protected readonly erreurDetail = signal<SupportErreur | null>(null);

  // Sante
  protected readonly sondes = signal<SupportSonde[] | null>(null);
  protected readonly sondage = signal(false);

  // FAQ
  protected readonly faq = signal<SupportFaq[] | null>(null);
  protected readonly faqArchives = signal(false);
  protected readonly faqOuvert = signal(false);
  protected readonly faqCible = signal<SupportFaq | null>(null);
  editionFaq: EditionFaq = { categorie: '', question: '', reponse: '', ordre: null };
  protected readonly faqParCategorie = computed(() => {
    const groupes = new Map<string, SupportFaq[]>();
    for (const f of this.faq() ?? []) {
      groupes.set(f.categorie, [...(groupes.get(f.categorie) ?? []), f]);
    }
    return [...groupes.entries()].map(([categorie, questions]) => ({ categorie, questions }));
  });

  ngOnInit(): void {
    this.choisir('mes');
  }

  choisir(o: Onglet): void {
    this.onglet.set(o);
    switch (o) {
      case 'mes':
      case 'tous':
        this.filtreStatut.set('');
        this.chargerTickets();
        if (o === 'tous' && !this.intervenants().length) {
          this.api.get<SupportIntervenant[]>('/api/support/intervenants').subscribe(i => this.intervenants.set(i));
        }
        break;
      case 'diagnostic':
        this.chargerErreurs();
        break;
      case 'sante':
        if (!this.sondes()) this.sonder();
        break;
      case 'faq':
        this.chargerFaq();
        break;
    }
  }

  // ---- Tickets ----

  chargerTickets(): void {
    this.tickets.set(null);
    const url = this.onglet() === 'tous' ? '/api/support/tickets/tous' : '/api/support/tickets';
    this.api.get<SupportTicket[]>(url, { statut: this.filtreStatut() }).subscribe(t => this.tickets.set(t));
  }

  filtrer(statut: string): void {
    this.filtreStatut.set(statut);
    this.chargerTickets();
  }

  nouveau(): void {
    this.edition = { sujet: '', categorie: 'QUESTION', priorite: 'NORMALE', message: '' };
    this.erreur.set(null);
    this.nouveauOuvert.set(true);
  }

  ouvrirTicket(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<SupportTicket>('/api/support/tickets', { ...this.edition, sujet: this.edition.sujet.trim() }).subscribe({
      next: t => {
        this.enCours.set(false);
        this.nouveauOuvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Ticket ouvert', detail: `n° ${t.id} · ${t.sujet}` });
        if (this.onglet() !== 'mes') this.onglet.set('mes');
        this.chargerTickets();
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Ouverture impossible.'); }
    });
  }

  consulter(t: SupportTicket): void {
    this.reponse = '';
    this.api.get<SupportTicket>(`/api/support/tickets/${t.id}`).subscribe(d => this.ticket.set(d));
  }

  fermerTicket(): void {
    this.ticket.set(null);
    this.chargerTickets();
  }

  repondre(): void {
    const t = this.ticket();
    if (!t || !this.reponse.trim()) return;
    this.enCours.set(true);
    this.api.post<SupportTicket>(`/api/support/tickets/${t.id}/messages`, { contenu: this.reponse.trim() }).subscribe({
      next: d => { this.enCours.set(false); this.reponse = ''; this.ticket.set(d); },
      error: () => this.enCours.set(false)
    });
  }

  changerStatut(statut: string): void {
    const t = this.ticket();
    if (!t) return;
    this.api.post<SupportTicket>(`/api/support/tickets/${t.id}/statut`, { statut }).subscribe(d => this.ticket.set(d));
  }

  affecter(utilisateurId: number | null): void {
    const t = this.ticket();
    if (!t) return;
    this.api.post<SupportTicket>(`/api/support/tickets/${t.id}/affectation`, { utilisateurId })
      .subscribe(d => this.ticket.set(d));
  }

  // ---- Diagnostic ----

  chargerErreurs(): void {
    this.erreurs.set(null);
    this.api.get<SupportErreur[]>('/api/support/erreurs', { traitees: this.traitees() }).subscribe(e => this.erreurs.set(e));
  }

  basculerTraitees(): void {
    this.traitees.set(!this.traitees());
    this.chargerErreurs();
  }

  traiter(e: SupportErreur): void {
    this.api.post<SupportErreur>(`/api/support/erreurs/${e.id}/traitement`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Erreur traitée', detail: "Elle reviendra dans la liste si elle survient de nouveau." });
      this.erreurDetail.set(null);
      this.chargerErreurs();
    });
  }

  // ---- Sante ----

  sonder(): void {
    this.sondage.set(true);
    this.api.get<SupportSonde[]>('/api/support/sante').subscribe({
      next: s => { this.sondage.set(false); this.sondes.set(s); },
      error: () => this.sondage.set(false)
    });
  }

  // ---- FAQ ----

  chargerFaq(): void {
    this.faq.set(null);
    this.api.get<SupportFaq[]>(this.faqArchives() ? '/api/support/faq/archives' : '/api/support/faq').subscribe(f => this.faq.set(f));
  }

  basculerFaqArchives(): void {
    this.faqArchives.set(!this.faqArchives());
    this.chargerFaq();
  }

  nouvelleQuestion(): void {
    this.faqCible.set(null);
    this.editionFaq = { categorie: '', question: '', reponse: '', ordre: null };
    this.erreur.set(null);
    this.faqOuvert.set(true);
  }

  modifierQuestion(f: SupportFaq): void {
    this.faqCible.set(f);
    this.editionFaq = { categorie: f.categorie, question: f.question, reponse: f.reponse, ordre: f.ordre };
    this.erreur.set(null);
    this.faqOuvert.set(true);
  }

  enregistrerQuestion(): void {
    const f = this.faqCible();
    this.enCours.set(true);
    this.erreur.set(null);
    (f ? this.api.putSilencieux<SupportFaq>(`/api/support/faq/${f.id}`, this.editionFaq)
       : this.api.postSilencieux<SupportFaq>('/api/support/faq', this.editionFaq)).subscribe({
      next: () => {
        this.enCours.set(false);
        this.faqOuvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Question enregistrée' });
        this.chargerFaq();
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.'); }
    });
  }

  changerStatutQuestion(f: SupportFaq, action: 'archivage' | 'reactivation'): void {
    this.api.post<SupportFaq>(`/api/support/faq/${f.id}/${action}`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: action === 'archivage' ? 'Question archivée' : 'Question réactivée', detail: f.question });
      this.chargerFaq();
    });
  }
}
