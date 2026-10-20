import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { ChartModule } from 'primeng/chart';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { JourPipe } from '../../core/format';
import { SupportErreur, SupportExperience, SupportExperienceLigne, SupportFaq, SupportIntervenant, SupportSonde, SupportTicket } from '../../core/modeles';

type Onglet = 'mes' | 'tous' | 'diagnostic' | 'sante' | 'experience' | 'faq';

const STATUTS: Record<string, string> = { OUVERT: 'Ouvert', EN_COURS: 'En cours', RESOLU: 'Résolu', FERME: 'Fermé' };
const CATEGORIES: Record<string, string> = { QUESTION: 'Question', INCIDENT: 'Incident', DEMANDE: 'Demande' };
const PRIORITES: Record<string, string> = { NORMALE: 'Normale', HAUTE: 'Haute', URGENTE: 'Urgente' };
const ORIGINES: Record<string, string> = { SERVEUR: 'Serveur', NAVIGATEUR: 'Navigateur', LENTEUR: 'Lenteur', SURVEILLANCE: 'Surveillance' };
const PERIODES: { heures: number; libelle: string }[] = [
  { heures: 1, libelle: 'Dernière heure' }, { heures: 6, libelle: '6 dernières heures' }, { heures: 24, libelle: '24 dernières heures' },
  { heures: 168, libelle: '7 derniers jours' }, { heures: 336, libelle: '14 derniers jours' }
];

/** Couleur de la charte lue dans les variables CSS, avec sa valeur livree en secours. */
function couleur(nom: string, defaut: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(nom).trim() || defaut;
}

interface EditionTicket { sujet: string; categorie: string; priorite: string; message: string; }
interface EditionFaq { categorie: string; question: string; reponse: string; ordre: number | null; }

/**
 * Centre de support : chacun ouvre et suit ses tickets et lit la FAQ ; le titulaire du droit Support traite tous les tickets
 * (dont ceux ouverts automatiquement par une erreur), consulte le diagnostic (erreurs, lenteurs, alertes du serveur), la sante,
 * l'experience vecue par les utilisateurs, exporte le dossier de diagnostic et tient la FAQ. Rien ne se supprime.
 */
@Component({
  selector: 'app-support',
  imports: [TooltipModule, FormsModule, DialogModule, ChartModule, JourPipe],
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
  protected readonly origines = ORIGINES;
  protected readonly codesOrigine = Object.keys(ORIGINES);
  protected readonly periodes = PERIODES;

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
  protected readonly filtreOrigine = signal('');
  protected readonly erreurDetail = signal<SupportErreur | null>(null);

  // Experience utilisateur
  protected readonly experience = signal<SupportExperience | null>(null);
  protected readonly heures = signal(24);
  protected readonly resumeAppels = computed(() => this.experience()?.resume.find(r => r.cle === 'API') ?? null);
  protected readonly resumeEcrans = computed(() => this.experience()?.resume.find(r => r.cle === 'ECRAN') ?? null);
  protected readonly courbe = computed(() => {
    const e = this.experience();
    if (!e || e.serie.length < 2) return null;
    const bleu = couleur('--bleu', '#05253D');
    const vert = couleur('--vert', '#51D78E');
    const heureSeule = e.heures <= 24;
    return {
      labels: e.serie.map(p => {
        const d = new Date(p.le + 'Z');
        const heure = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
        return heureSeule ? heure : `${d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })} ${heure}`;
      }),
      datasets: [
        { label: 'Appel moyen (ms)', data: e.serie.map(p => p.appels ? p.moyenneAppelMs : null), borderColor: bleu, backgroundColor: bleu,
          tension: 0.3, pointRadius: 2, spanGaps: true },
        { label: 'Ouverture d\'écran moyenne (ms)', data: e.serie.map(p => p.moyenneEcranMs || null), borderColor: vert, backgroundColor: vert,
          tension: 0.3, pointRadius: 2, spanGaps: true }
      ]
    };
  });
  protected readonly optionsCourbe = {
    maintainAspectRatio: false, animation: false, interaction: { mode: 'index', intersect: false },
    plugins: { legend: { position: 'bottom' } },
    scales: { y: { beginAtZero: true, ticks: { callback: (v: number) => `${v} ms` } } }
  };

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
      case 'experience':
        this.chargerExperience();
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
    this.api.get<SupportErreur[]>('/api/support/erreurs', { traitees: this.traitees(), origine: this.filtreOrigine() })
      .subscribe(e => this.erreurs.set(e));
  }

  filtrerOrigine(origine: string): void {
    this.filtreOrigine.set(origine);
    this.chargerErreurs();
  }

  /** Ouvre le detail d'une erreur depuis le ticket qu'elle a ouvert. */
  voirErreur(id: number): void {
    this.api.get<SupportErreur>(`/api/support/erreurs/${id}`).subscribe(e => this.erreurDetail.set(e));
  }

  /** Ouvre le ticket d'une erreur depuis son detail. */
  voirTicket(id: number): void {
    this.erreurDetail.set(null);
    if (!this.intervenants().length) {
      this.api.get<SupportIntervenant[]>('/api/support/intervenants').subscribe(i => this.intervenants.set(i));
    }
    this.consulter({ id } as SupportTicket);
  }

  creerTicket(e: SupportErreur): void {
    this.api.post<SupportTicket>(`/api/support/erreurs/${e.id}/ticket`).subscribe(t => {
      this.messages.add({ severity: 'success', summary: 'Ticket ouvert', detail: `n° ${t.id} · ${t.sujet}` });
      this.erreurDetail.set(null);
      this.chargerErreurs();
      this.voirTicket(t.id);
    });
  }

  /** Ticket rattache encore actif : l'erreur est suivie, pas besoin d'en ouvrir un autre. */
  ticketActif(e: SupportErreur): boolean {
    return e.ticketId !== null && e.ticketStatut !== 'FERME';
  }

  exporter(): void {
    this.api.telecharger('/api/support/diagnostic/export', 'diagnostic-crm.zip');
  }

  // ---- Experience utilisateur ----

  chargerExperience(): void {
    this.experience.set(null);
    this.api.get<SupportExperience>('/api/support/experience', { heures: this.heures() }).subscribe(e => this.experience.set(e));
  }

  choisirPeriode(heures: number): void {
    this.heures.set(Number(heures));
    this.chargerExperience();
  }

  /** Lenteur ressentie : moyenne d'au moins 3 s ou 10 % des mesures a 3 s et plus. */
  lent(l: SupportExperienceLigne): boolean {
    return l.moyenneMs >= 3000 || l.part3s >= 10;
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
