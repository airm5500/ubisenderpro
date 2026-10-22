import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { CampagneMarketing, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, pourcent } from '../../core/format';
import { CampagneMarketingFormulaire } from './campagne-marketing-formulaire';

interface Onglet { statut: string; libelle: string; }

@Component({
  selector: 'app-campagnes-marketing',
  imports: [FormsModule, RouterLink, JourPipe, CampagneMarketingFormulaire],
  templateUrl: './campagnes-marketing.html'
})
export class CampagnesMarketing implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;
  protected readonly pc = pourcent;

  protected readonly onglets: Onglet[] = [
    { statut: 'A_VALIDER', libelle: 'À valider' }, { statut: 'VALIDEE', libelle: 'Programmées' }, { statut: 'EN_COURS', libelle: 'En cours' },
    { statut: 'TERMINEE', libelle: 'Exécutées' }, { statut: 'ANNULEE', libelle: 'Annulées' }, { statut: 'BROUILLON', libelle: 'Brouillons' },
    { statut: '', libelle: 'Toutes' }
  ];
  protected readonly onglet = signal('');
  protected readonly compteurs = signal<Record<string, number>>({});
  protected readonly page = signal<Page<CampagneMarketing> | null>(null);
  protected readonly saisie = signal(false);
  protected readonly produitsAnnonces = signal<number[]>([]);
  q = '';
  numero = 0;
  private readonly taille = 25;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    // Depuis le catalogue : « Annoncer leur retour » ouvre une campagne avec ces produits
    const produits = this.route.snapshot.queryParamMap.get('produits');
    if (produits) {
      this.produitsAnnonces.set(produits.split(',').map(Number).filter(n => n > 0));
      this.saisie.set(true);
    }
    // Depuis « Ma journée » : « Nouvelle campagne » ouvre directement la fenetre de creation
    if (this.route.snapshot.queryParamMap.has('nouvelle')) this.saisie.set(true);
    const requetes = Object.fromEntries(this.onglets.map(o =>
      [o.statut || 'TOUTES', this.api.get<Page<CampagneMarketing>>('/api/marketing/campagnes', { statut: o.statut, page: 0, taille: 1 })]));
    forkJoin(requetes).subscribe(r => {
      const c = Object.fromEntries(Object.entries(r).map(([k, p]) => [k, p.total]));
      this.compteurs.set(c);
      this.onglet.set(this.auth.peut('CAMPAGNES_MARKETING_VALIDER') && c['A_VALIDER'] > 0 ? 'A_VALIDER' : '');
      this.charger();
    });
  }

  choisirOnglet(statut: string): void {
    this.onglet.set(statut);
    this.rechercher();
  }

  rechercher(): void {
    this.numero = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numero = Math.min(Math.max(0, this.numero + decalage), this.nbPages() - 1);
    this.charger();
  }

  creee(c: CampagneMarketing): void {
    this.router.navigate(['/marketing/campagnes', c.id]);
  }

  private charger(): void {
    this.api.get<Page<CampagneMarketing>>('/api/marketing/campagnes', { q: this.q, statut: this.onglet(), page: this.numero, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }
}
