import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';

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
      @if (auth.peut('TABLEAU_BORD_MARKETING')) {
        <a class="bloc tuile" routerLink="/marketing/tableau-de-bord" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Tableau de bord</b>
          <span class="discret">Campagnes, messages envoyés et lus par canal, réponses, désabonnements et promotions en cours.</span>
        </a>
      }
      @if (auth.peut('CAMPAGNES_MARKETING')) {
        <a class="bloc tuile" routerLink="/marketing/campagnes" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Campagnes promotionnelles</b>
          <span class="discret">WhatsApp, e-mail ou SMS vers une liste de diffusion, après validation par le directeur d'agence.</span>
        </a>
      }
      @if (auth.peut('LISTES')) {
        <a class="bloc tuile" routerLink="/marketing/listes" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Listes de diffusion</b>
          <span class="discret">Les clients visés par vos campagnes, par critères ou choisis un par un. Les clients qui répondent STOP sont écartés.</span>
        </a>
      }
      @if (auth.peut('PRODUITS')) {
        <a class="bloc tuile" routerLink="/marketing/catalogue" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Catalogue des produits</b>
          <span class="discret">Les produits à mettre en avant, importés depuis votre fichier Excel ou saisis à la main.</span>
        </a>
      }
      @if (auth.peut('PROMOTIONS')) {
        <a class="bloc tuile" routerLink="/marketing/promotions" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Promotions</b>
          <span class="discret">Produits du catalogue en unités gratuites ou à prix promotionnel, sur une période ; vos campagnes les citent.</span>
        </a>
      }
      @if (auth.recouvrement()) { <a routerLink="/">Revenir au Recouvrement</a> }
    </div>`
})
export class Marketing {
  protected readonly auth = inject(Auth);
}
