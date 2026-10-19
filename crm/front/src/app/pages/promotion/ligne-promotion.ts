import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Page, Produit, PromotionProduit } from '../../core/modeles';
import { MontantPipe } from '../../core/format';

interface Formulaire {
  produit: Produit | null; quantiteMin: number | null; avantage: 'UG_QUANTITE' | 'UG_POURCENTAGE' | 'PRIX';
  ugQuantite: number | null; ugPourcentage: number | null; ugPlafond: number | null; prixPromo: number | null;
}

/** Ajout d'un produit du catalogue a une promotion, ou modification de son avantage. */
@Component({
  selector: 'app-ligne-promotion',
  imports: [FormsModule, DialogModule, MontantPipe],
  templateUrl: './ligne-promotion.html'
})
export class LignePromotion {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  readonly ouvert = model(false);
  readonly promotionId = input.required<number>();
  readonly ligne = input<PromotionProduit | null>(null);
  readonly enregistre = output<PromotionProduit>();

  protected readonly resultats = signal<Produit[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  recherche = '';
  formulaire: Formulaire = vide();
  private readonly frappe = new Subject<string>();

  constructor() {
    this.frappe.pipe(debounceTime(250), switchMap(q => this.api.get<Page<Produit>>('/api/marketing/produits', { q, taille: 8 })))
      .subscribe(p => this.resultats.set(p.elements));
  }

  preparer(): void {
    const l = this.ligne();
    this.erreur.set(null);
    this.recherche = '';
    this.resultats.set([]);
    this.formulaire = l ? {
      produit: { id: l.produitId, code: l.produitCode, libelle: l.produitLibelle, prix: l.prixPublic } as Produit,
      quantiteMin: l.quantiteMin, avantage: l.avantage, ugQuantite: l.ugQuantite, ugPourcentage: l.ugPourcentage, ugPlafond: l.ugPlafond,
      prixPromo: l.prixPromo
    } : vide();
  }

  chercher(): void {
    this.frappe.next(this.recherche.trim());
  }

  choisir(p: Produit): void {
    this.formulaire.produit = p;
    this.resultats.set([]);
    this.recherche = '';
  }

  enregistrer(): void {
    const l = this.ligne();
    const f = this.formulaire;
    const corps = {
      produitId: f.produit?.id, quantiteMin: f.quantiteMin || null, avantage: f.avantage,
      ugQuantite: f.avantage === 'UG_QUANTITE' ? f.ugQuantite : null,
      ugPourcentage: f.avantage === 'UG_POURCENTAGE' ? f.ugPourcentage : null,
      ugPlafond: f.avantage === 'UG_POURCENTAGE' ? f.ugPlafond || null : null,
      prixPromo: f.avantage === 'PRIX' ? f.prixPromo : null
    };
    const base = `/api/marketing/promotions/${this.promotionId()}/produits`;
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = l ? this.api.putSilencieux<PromotionProduit>(`${base}/${l.id}`, corps) : this.api.postSilencieux<PromotionProduit>(base, corps);
    requete.subscribe({
      next: ligne => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: l ? 'Avantage enregistré' : 'Produit ajouté', detail: ligne.produitLibelle + ' : ' + ligne.resume });
        this.enregistre.emit(ligne);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.champs?.[0]?.message ?? e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}

function vide(): Formulaire {
  return { produit: null, quantiteMin: 1, avantage: 'UG_QUANTITE', ugQuantite: null, ugPourcentage: null, ugPlafond: null, prixPromo: null };
}
