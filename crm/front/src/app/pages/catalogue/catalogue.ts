import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { FiltresCatalogue, Page, Produit } from '../../core/modeles';
import { JourPipe, MontantPipe } from '../../core/format';
import { ProduitFormulaire } from './produit-formulaire';

/** Catalogue des produits du Marketing : import Excel ou saisie a la main ; un produit s'archive, il ne se supprime pas. */
@Component({
  selector: 'app-catalogue',
  imports: [FormsModule, RouterLink, JourPipe, MontantPipe, ProduitFormulaire],
  templateUrl: './catalogue.html'
})
export class Catalogue implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);

  criteres = { q: '', categorie: '', laboratoire: '', statut: 'ACTIF' };
  protected readonly page = signal<Page<Produit> | null>(null);
  protected readonly filtres = signal<FiltresCatalogue | null>(null);
  protected readonly saisie = signal(false);
  protected readonly cible = signal<Produit | null>(null);
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    this.chargerFiltres();
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  nouveau(): void {
    this.cible.set(null);
    this.saisie.set(true);
  }

  modifier(p: Produit): void {
    this.cible.set(p);
    this.saisie.set(true);
  }

  enregistre(): void {
    this.chargerFiltres();
    this.charger();
  }

  archiver(p: Produit): void {
    this.api.post(`/api/marketing/produits/${p.id}/archivage`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Produit archivé', detail: p.libelle });
      this.enregistre();
    });
  }

  reactiver(p: Produit): void {
    this.api.post(`/api/marketing/produits/${p.id}/reactivation`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Produit réactivé', detail: p.libelle });
      this.enregistre();
    });
  }

  private chargerFiltres(): void {
    this.api.get<FiltresCatalogue>('/api/marketing/produits/filtres').subscribe(f => this.filtres.set(f));
  }

  private charger(): void {
    this.api.get<Page<Produit>>('/api/marketing/produits', { ...this.criteres, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
