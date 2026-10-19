import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { ModuleCourant } from '../../core/module-courant';

/** Accueil du module Marketing : les ecrans en service et ceux qui arrivent. */
@Component({
  selector: 'app-marketing',
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="entete"><div>
        <h1>Marketing</h1>
        <p class="sous-titre">Listes de diffusion, campagnes promotionnelles WhatsApp, e-mail et SMS, catalogue des produits et promotions.</p>
      </div></div>
      <div class="raccourcis">
      @if (auth.peut('TABLEAU_BORD_MARKETING')) {
        <a class="raccourci" routerLink="/marketing/tableau-de-bord">
          <b>Tableau de bord <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">Campagnes, messages envoyés et lus par canal, réponses, désabonnements et promotions en cours.</span>
        </a>
      }
      @if (auth.peut('CAMPAGNES_MARKETING')) {
        <a class="raccourci" routerLink="/marketing/campagnes">
          <b>Campagnes promotionnelles <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">WhatsApp, e-mail ou SMS vers une liste de diffusion, après validation par le directeur d'agence.</span>
        </a>
      }
      @if (auth.peut('LISTES')) {
        <a class="raccourci" routerLink="/marketing/listes">
          <b>Listes de diffusion <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">Les clients visés par vos campagnes, par critères ou choisis un par un. Les clients qui répondent STOP sont écartés.</span>
        </a>
      }
      @if (auth.peut('PRODUITS')) {
        <a class="raccourci" routerLink="/marketing/catalogue">
          <b>Catalogue des produits <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">Les produits à mettre en avant, importés depuis votre fichier Excel ou saisis à la main.</span>
        </a>
      }
      @if (auth.peut('PROMOTIONS')) {
        <a class="raccourci" routerLink="/marketing/promotions">
          <b>Promotions <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">Produits du catalogue en unités gratuites ou à prix promotionnel, sur une période ; vos campagnes les citent.</span>
        </a>
      }
      @if (auth.peut('CLIENTS')) {
        <a class="raccourci" routerLink="/clients">
          <b>Clients <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">La base clients commune : fiches, contacts WhatsApp, segments et désabonnements.</span>
        </a>
      }
      @if (auth.peut('DISCUSSIONS')) {
        <a class="raccourci" routerLink="/discussions">
          <b>Discussions <i class="pi pi-arrow-right" aria-hidden="true"></i></b>
          <span class="discret">Les échanges WhatsApp avec chaque client ; répondre ou écrire à un client depuis votre numéro.</span>
        </a>
      }
      </div>
      @if (auth.recouvrement()) { <a routerLink="/" (click)="module.choisir('RECOUVREMENT')">Revenir au Recouvrement</a> }
    </div>`
})
export class Marketing {
  protected readonly auth = inject(Auth);
  protected readonly module = inject(ModuleCourant);
}
