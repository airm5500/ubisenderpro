import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { FiltresCatalogue, Produit } from '../../core/modeles';

interface Formulaire {
  code: string; libelle: string; categorie: string; laboratoire: string; conditionnement: string; prix: number | null; description: string;
}

/** Saisie ou modification d'un produit du catalogue, a la main. */
@Component({
  selector: 'app-produit-formulaire',
  imports: [FormsModule, DialogModule],
  templateUrl: './produit-formulaire.html'
})
export class ProduitFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  readonly ouvert = model(false);
  readonly produit = input<Produit | null>(null);
  /** Categories et laboratoires deja saisis, proposes a la frappe. */
  readonly filtres = input<FiltresCatalogue | null>(null);
  readonly enregistre = output<Produit>();

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = vide();

  preparer(): void {
    const p = this.produit();
    this.erreur.set(null);
    this.formulaire = p ? {
      code: p.code, libelle: p.libelle, categorie: p.categorie ?? '', laboratoire: p.laboratoire ?? '', conditionnement: p.conditionnement ?? '',
      prix: p.prix, description: p.description ?? ''
    } : vide();
  }

  enregistrer(): void {
    const p = this.produit();
    const f = this.formulaire;
    const corps = { ...f, code: f.code.trim(), libelle: f.libelle.trim(), prix: f.prix === null || (f.prix as unknown) === '' ? null : f.prix };
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = p ? this.api.putSilencieux<Produit>(`/api/marketing/produits/${p.id}`, corps)
      : this.api.postSilencieux<Produit>('/api/marketing/produits', corps);
    requete.subscribe({
      next: produit => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: p ? 'Produit enregistré' : 'Produit ajouté', detail: produit.libelle });
        this.enregistre.emit(produit);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.champs?.[0]?.message ?? e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}

function vide(): Formulaire {
  return { code: '', libelle: '', categorie: '', laboratoire: '', conditionnement: '', prix: null, description: '' };
}
