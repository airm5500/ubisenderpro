import { Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Subject, debounceTime, map, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
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
  protected readonly auth = inject(Auth);

  readonly ouvert = model(false);
  readonly promotionId = input.required<number>();
  readonly ligne = input<PromotionProduit | null>(null);
  readonly enregistre = output<PromotionProduit>();

  protected readonly resultats = signal<Produit[]>([]);
  /** Recherche terminee sans resultat : on le dit, au lieu de laisser le bouton grise sans explication. */
  protected readonly aucun = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  recherche = '';
  formulaire: Formulaire = vide();
  /** Creation rapide d'un produit absent du catalogue (remarque d'Airman du 22/10/2026), pour qui gere le catalogue. */
  nouveau: { code: string; libelle: string; laboratoire: string; prix: number | null } | null = null;
  private readonly frappe = new Subject<string>();

  constructor() {
    // Le formulaire se prepare des l'ouverture, pas a la fin de l'animation : une saisie rapide n'est plus effacee
    effect(() => { if (this.ouvert()) untracked(() => this.preparer()); });
    this.frappe.pipe(debounceTime(250), switchMap(q => this.api.get<Page<Produit>>('/api/marketing/produits', { q, taille: 8 })
      .pipe(map(p => ({ q, produits: p.elements })))))
      .subscribe(({ q, produits }) => {
        const exact = produits.filter(p => [p.code, `${p.code} ${p.libelle}`].some(t => t.toLowerCase() === q.toLowerCase()));
        if (exact.length === 1) {
          this.choisir(exact[0]);
          return;
        }
        this.resultats.set(produits);
        this.aucun.set(!!q && !produits.length);
      });
  }

  preparer(): void {
    const l = this.ligne();
    this.erreur.set(null);
    this.recherche = '';
    this.nouveau = null;
    this.resultats.set([]);
    this.aucun.set(false);
    this.formulaire = l ? {
      produit: { id: l.produitId, code: l.produitCode, libelle: l.produitLibelle, prix: l.prixPublic } as Produit,
      quantiteMin: l.quantiteMin, avantage: l.avantage, ugQuantite: l.ugQuantite, ugPourcentage: l.ugPourcentage, ugPlafond: l.ugPlafond,
      prixPromo: l.prixPromo
    } : vide();
  }

  chercher(): void {
    this.aucun.set(false);
    this.nouveau = null;
    const q = this.recherche.trim();
    if (!q) {
      this.resultats.set([]);
      return;
    }
    this.frappe.next(q);
  }

  choisir(p: Produit): void {
    this.formulaire.produit = p;
    this.resultats.set([]);
    this.aucun.set(false);
    this.recherche = '';
  }

  /** Le texte cherche sert de code s'il tient en un mot avec un chiffre, sinon de libelle. */
  creerProduit(): void {
    const q = this.recherche.trim();
    const code = /^\S*\d\S*$/.test(q);
    this.nouveau = { code: code ? q : '', libelle: code ? '' : q, laboratoire: '', prix: null };
    this.erreur.set(null);
  }

  enregistrerProduit(): void {
    const n = this.nouveau!;
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<Produit>('/api/marketing/produits', {
      code: n.code.trim(), libelle: n.libelle.trim(), laboratoire: n.laboratoire.trim() || null, prix: n.prix
    }).subscribe({
      next: p => {
        this.enCours.set(false);
        this.nouveau = null;
        this.choisir(p);
        this.messages.add({ severity: 'success', summary: 'Produit créé', detail: `${p.libelle} · ${p.code} est ajouté au catalogue.` });
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.champs?.[0]?.message ?? e?.error?.message ?? 'Création impossible.');
      }
    });
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
