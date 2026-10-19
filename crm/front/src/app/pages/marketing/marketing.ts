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
        <p class="sous-titre">Listes de diffusion, puis campagnes promotionnelles WhatsApp, e-mail et SMS.</p>
      </div></div>
      @if (auth.peut('LISTES')) {
        <a class="bloc tuile" routerLink="/marketing/listes" style="text-decoration: none; max-width: 420px">
          <b style="font-size: 16px">Listes de diffusion</b>
          <span class="discret">Les clients visés par vos campagnes, par critères ou choisis un par un. Les clients qui répondent STOP sont écartés.</span>
        </a>
      }
      <div class="info"><i class="pi pi-info-circle"></i><span>Les campagnes marketing, le catalogue et les promotions arrivent dans les prochaines livraisons.</span></div>
      @if (auth.recouvrement()) { <a routerLink="/">Revenir au Recouvrement</a> }
    </div>`
})
export class Marketing {
  protected readonly auth = inject(Auth);
}
