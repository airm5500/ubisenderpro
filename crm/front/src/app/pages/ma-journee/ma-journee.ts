import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FenetreCampagne } from '../../core/fenetre-campagne';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Campagne, EtatSession, Facture, MessageRecu, Page, PortefeuilleClient } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, aujourdhui } from '../../core/format';

interface AFaire { titre: string; detail: string; action: string; lien: string; parametres?: Record<string, string>; couleur: string; }

const JOURS = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'];

@Component({
  selector: 'app-ma-journee',
  imports: [RouterLink, JourPipe, MontantPipe],
  templateUrl: './ma-journee.html'
})
export class MaJournee implements OnInit {
  protected readonly fenetreCampagne = inject(FenetreCampagne);
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  protected readonly dateDuJour = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Abidjan' })
    .format(new Date()).replace(/^./, c => c.toUpperCase());
  protected readonly prenom = computed(() => (this.auth.utilisateur()?.nomComplet ?? '').split(' ')[0]);

  protected readonly portefeuille = signal<PortefeuilleClient[]>([]);
  protected readonly nbClients = signal(0);
  protected readonly nbEchues = signal(0);
  protected readonly nbARelancer = signal(0);
  protected readonly echeances = signal<Facture[]>([]);
  protected readonly campagnes = signal<Campagne[]>([]);
  protected readonly reponses = signal<MessageRecu[]>([]);
  protected readonly session = signal<EtatSession | null>(null);

  protected readonly totaux = computed(() => ({
    factures: this.portefeuille().reduce((s, c) => s + c.nbFacturesOuvertes, 0),
    solde: this.portefeuille().reduce((s, c) => s + (c.totalSolde ?? 0), 0)
  }));

  /** Factures arrivant a echeance sur 14 jours, jour par jour. */
  protected readonly histogramme = computed(() => {
    const jours = Array.from({ length: 14 }, (_, i) => {
      const iso = aujourdhui(i);
      const d = new Date(iso + 'T00:00:00Z');
      return { iso, libelle: i === 0 ? 'Auj.' : `${JOURS[d.getUTCDay()]} ${d.getUTCDate()}`, nb: 0, programmees: 0 };
    });
    for (const f of this.echeances()) {
      const j = jours.find(x => x.iso === f.dateEcheance);
      if (j) { j.nb++; if (f.statut === 'RELANCE_PROGRAMMEE') j.programmees++; }
    }
    const max = Math.max(1, ...jours.map(j => j.nb));
    return jours.map(j => ({ ...j, hauteur: j.nb / max * 120 }));
  });

  protected readonly resultats = computed(() => {
    const c = this.campagnes().filter(x => x.statut === 'EN_COURS' || x.statut === 'TERMINEE');
    const envoyes = c.reduce((s, x) => s + x.nbEnvoyes, 0);
    const echecs = c.reduce((s, x) => s + x.nbEchecs, 0);
    const total = envoyes + echecs;
    return { envoyes, echecs, reussite: total ? Math.round(envoyes / total * 100) : null, reponses: this.reponses().length };
  });

  protected readonly aFaire = computed<AFaire[]>(() => {
    const liste: AFaire[] = [];
    const s = this.session();
    if (s?.statut === 'INJOIGNABLE') {
      liste.push({ titre: 'Le service d\'envoi WhatsApp ne répond pas', detail: 'Vos relances attendent sans être perdues. Prévenez l\'administrateur de la plateforme.',
        action: 'Voir mon numéro', lien: '/numeros', couleur: '#C2410C' });
    } else if (s && s.statut !== 'CONNECTE') {
      liste.push({ titre: 'Votre numéro WhatsApp n\'est pas connecté', detail: 'Vos relances attendent : scannez le QR code avec le téléphone professionnel.',
        action: 'Connecter mon numéro', lien: '/numeros', couleur: '#C2410C' });
    }
    for (const c of this.campagnes().filter(x => x.statut === 'BROUILLON' && x.motifRejet)) {
      liste.push({ titre: `Campagne à corriger · ${c.nom}`, detail: `Renvoyée par ${c.rejetePar ?? 'la direction'} : ${c.motifRejet}`,
        action: 'Corriger', lien: `/campagnes/${c.id}/modifier`, couleur: '#C2410C' });
    }
    if (this.nbARelancer() > 0) {
      liste.push({ titre: `${this.nbARelancer()} facture${this.nbARelancer() > 1 ? 's' : ''} échue${this.nbARelancer() > 1 ? 's' : ''} sans relance`,
        detail: 'Échues et pas encore programmées dans une campagne.', action: 'Voir', lien: '/factures', couleur: '#F4B740' });
    }
    const recentes = this.reponses().filter(r => r.recuLe >= aujourdhui(-1));
    if (recentes.length) {
      liste.push({ titre: `${recentes.length} réponse${recentes.length > 1 ? 's' : ''} de clients depuis hier`,
        detail: 'Reçues sur votre WhatsApp professionnel ; une copie est gardée dans la fiche client.', action: 'Lire', lien: '/reponses', couleur: '#51D78E' });
    }
    for (const c of this.campagnes().filter(x => x.statut === 'BROUILLON' && !x.motifRejet)) {
      liste.push({ titre: `Brouillon non soumis · ${c.nom}`, detail: 'Soumettez-le à validation pour qu\'il parte à la date prévue.',
        action: 'Reprendre', lien: `/campagnes/${c.id}`, couleur: '#9DB0BF' });
    }
    return liste;
  });

  ngOnInit(): void {
    this.api.toutes<PortefeuilleClient>('/api/recouvrement/portefeuille', { avecSolde: true }, 200).subscribe(c => this.portefeuille.set(c));
    this.api.get<Page<PortefeuilleClient>>('/api/recouvrement/portefeuille', { page: 0, taille: 1 }).subscribe(p => this.nbClients.set(p.total));
    this.api.get<Page<Facture>>('/api/recouvrement/factures', { enRetard: true, page: 0, taille: 1 }).subscribe(p => this.nbEchues.set(p.total));
    this.api.get<Page<Facture>>('/api/recouvrement/factures', { enRetard: true, statut: 'A_RELANCER', page: 0, taille: 1 }).subscribe(p => this.nbARelancer.set(p.total));
    this.api.toutes<Facture>('/api/recouvrement/factures', { echeanceDu: aujourdhui(), echeanceAu: aujourdhui(13), nature: 'FACTURE' }, 100)
      .subscribe(f => this.echeances.set(f.filter(x => x.solde > 0)));
    this.api.get<Page<Campagne>>('/api/recouvrement/campagnes', { page: 0, taille: 20 }).subscribe(p => this.campagnes.set(p.elements));
    this.api.get<Page<MessageRecu>>('/api/recouvrement/reponses', { page: 0, taille: 50 }).subscribe(p => this.reponses.set(p.elements));
    const id = this.auth.utilisateur()?.id;
    if (id) this.api.getSilencieux<EtatSession>(`/api/whatsapp/sessions/${id}`).subscribe({
      next: s => this.session.set(s),
      error: () => this.session.set({ statut: 'INJOIGNABLE', sante: null, raison: null, qr: null })
    });
  }
}
