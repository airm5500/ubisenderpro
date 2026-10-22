import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { CampagneMarketing, Evenement, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, aujourdhui, pourcent } from '../../core/format';

interface ReponseDuJour { clientId: number | null; raisonSociale: string | null; numeroClient: string; contenu: string | null; recuLe: string; campagneId: number; campagneNom: string; }
interface PromotionDuJour { id: number; nom: string; dateDebut: string; dateFin: string; statut: string; moment: 'COMMENCE' | 'FINIT'; }
interface Journee { horizonJours: number; nbReponsesDuJour: number | null; reponsesDuJour: ReponseDuJour[] | null; promotions: PromotionDuJour[] | null; evenements: Evenement[] | null; }
interface AFaire { titre: string; detail: string; action: string; lien: string; parametres?: Record<string, string>; couleur: string; }

const VIDE: Page<CampagneMarketing> = { elements: [], total: 0, page: 0, taille: 0 };

/** Ma journee Marketing, page d'arrivee du module (remarque 10 du 22/10/2026). */
@Component({
  selector: 'app-ma-journee-marketing',
  imports: [RouterLink, JourPipe],
  templateUrl: './marketing.html'
})
export class MaJourneeMarketing implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;
  protected readonly pc = pourcent;

  protected readonly dateDuJour = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Abidjan' })
    .format(new Date()).replace(/^./, c => c.toUpperCase());
  protected readonly prenom = computed(() => (this.auth.utilisateur()?.nomComplet ?? '').split(' ')[0]);

  protected readonly journee = signal<Journee | null>(null);
  protected readonly aValider = signal<CampagneMarketing[]>([]);
  protected readonly enCours = signal<CampagneMarketing[]>([]);
  protected readonly programmees = signal<CampagneMarketing[]>([]);
  protected readonly aCorriger = signal<CampagneMarketing[]>([]);

  /** Campagnes en cours et celles programmees aujourd'hui. */
  protected readonly duJour = computed(() => [...this.enCours(), ...this.programmees().filter(c => c.dateExecution?.slice(0, 10) === aujourdhui())]);

  protected readonly aFaire = computed<AFaire[]>(() => {
    const liste: AFaire[] = [];
    const valideur = this.auth.peut('CAMPAGNES_MARKETING_VALIDER');
    for (const c of this.aValider()) {
      liste.push(valideur
        ? { titre: `Campagne à valider · ${c.nom}`, detail: `Soumise par ${c.creePar}, ${c.nbClients} client${c.nbClients > 1 ? 's' : ''}.`, action: 'Examiner', lien: `/marketing/campagnes/${c.id}`, couleur: '#F4B740' }
        : { titre: `En attente de validation · ${c.nom}`, detail: 'Elle partira dès qu\'un valideur l\'aura acceptée.', action: 'Voir', lien: `/marketing/campagnes/${c.id}`, couleur: '#9DB0BF' });
    }
    for (const c of this.aCorriger()) {
      liste.push({ titre: `Campagne à corriger · ${c.nom}`, detail: `Renvoyée par ${c.rejetePar ?? 'le valideur'} : ${c.motifRejet}`,
        action: 'Corriger', lien: `/marketing/campagnes/${c.id}`, couleur: '#C2410C' });
    }
    const n = this.journee()?.nbReponsesDuJour ?? 0;
    if (n) {
      liste.push({ titre: `${n} réponse${n > 1 ? 's' : ''} de clients aujourd'hui`, detail: 'Réponses aux campagnes Marketing, à lire dans les discussions.',
        action: 'Lire', lien: '/discussions', couleur: '#51D78E' });
    }
    return liste;
  });

  ngOnInit(): void {
    this.api.get<Journee>('/api/marketing/ma-journee').subscribe(j => this.journee.set(j));
    if (!this.auth.peut('CAMPAGNES_MARKETING')) return;
    const page = (statut: string) => this.api.get<Page<CampagneMarketing>>('/api/marketing/campagnes', { statut, page: 0, taille: 50 });
    forkJoin({ aValider: page('A_VALIDER'), enCours: page('EN_COURS'), programmees: page('VALIDEE'),
      brouillons: this.auth.peut('CAMPAGNES_MARKETING_PREPARER') ? page('BROUILLON') : of(VIDE) }).subscribe(r => {
      this.aValider.set(r.aValider.elements);
      this.enCours.set(r.enCours.elements);
      this.programmees.set(r.programmees.elements);
      const moi = this.auth.utilisateur()?.id;
      this.aCorriger.set(r.brouillons.elements.filter(c => c.motifRejet && c.creeParId === moi));
    });
  }
}
