import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Promotion, PromotionProduit } from '../../core/modeles';
import { JourPipe, MontantPipe } from '../../core/format';
import { PromotionFormulaire } from '../promotions/promotion-formulaire';
import { STATUTS_PROMOTION } from '../promotions/statut-promotion';
import { LignePromotion } from './ligne-promotion';

/** Une promotion : sa periode, ses produits et leur avantage, son activation et son archivage. */
@Component({
  selector: 'app-promotion',
  imports: [TooltipModule, RouterLink, JourPipe, MontantPipe, PromotionFormulaire, LignePromotion],
  templateUrl: './promotion.html'
})
export class PromotionDetail implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly S = STATUTS_PROMOTION;

  readonly id = input.required<string>();
  /** Arrivee juste apres la creation : la saisie du premier produit s'ouvre d'office. */
  readonly nouvelle = input<string>();
  protected readonly promotion = signal<Promotion | null>(null);
  protected readonly lignes = signal<PromotionProduit[]>([]);
  protected readonly saisie = signal(false);
  protected readonly saisieLigne = signal(false);
  protected readonly cible = signal<PromotionProduit | null>(null);
  protected avecRetires = false;
  protected readonly modifiable = computed(() => this.auth.peut('PROMOTIONS_GERER') && this.promotion()?.statut !== 'ARCHIVEE');

  ngOnInit(): void {
    this.charger(!!this.nouvelle());
  }

  charger(premierProduit = false): void {
    this.api.get<Promotion>(`/api/marketing/promotions/${this.id()}`).subscribe(p => this.promotion.set(p));
    this.api.get<PromotionProduit[]>(`/api/marketing/promotions/${this.id()}/produits`, { avecRetires: this.avecRetires })
      .subscribe(l => {
        this.lignes.set(l);
        if (premierProduit && !l.length && this.auth.peut('PROMOTIONS_GERER')) this.ajouter();
      });
  }

  basculerRetires(): void {
    this.avecRetires = !this.avecRetires;
    this.charger();
  }

  ajouter(): void {
    this.cible.set(null);
    this.saisieLigne.set(true);
  }

  modifier(l: PromotionProduit): void {
    this.cible.set(l);
    this.saisieLigne.set(true);
  }

  retirer(l: PromotionProduit): void {
    this.api.post(`/api/marketing/promotions/${this.id()}/produits/${l.id}/retrait`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Produit retiré', detail: l.produitLibelle });
      this.charger();
    });
  }

  remettre(l: PromotionProduit): void {
    this.api.post(`/api/marketing/promotions/${this.id()}/produits`, {
      produitId: l.produitId, quantiteMin: l.quantiteMin, avantage: l.avantage, ugQuantite: l.ugQuantite, ugPourcentage: l.ugPourcentage,
      ugPlafond: l.ugPlafond, prixPromo: l.prixPromo
    }).subscribe(() => this.charger());
  }

  /** Debut a venir : l'activation la programme. */
  programmable(p: Promotion): boolean {
    return p.dateDebut > new Date().toISOString().substring(0, 10);
  }

  action(chemin: 'activation' | 'desactivation' | 'archivage' | 'reactivation', titre: string): void {
    this.api.post<Promotion>(`/api/marketing/promotions/${this.id()}/${chemin}`).subscribe(p => {
      this.messages.add({ severity: 'success', summary: titre, detail: p.nom });
      this.charger();
    });
  }

  exporter(): void {
    const p = this.promotion();
    if (p) this.api.telecharger(`/api/marketing/promotions/${p.id}/produits/export`, `promotion-${p.code}.csv`);
  }
}
