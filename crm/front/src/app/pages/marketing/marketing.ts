import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';

/** Le module Marketing arrive apres le pilote : page d'attente pour les comptes qui y sont habilites. */
@Component({
  selector: 'app-marketing',
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="entete"><div>
        <h1>Marketing</h1>
        <p class="sous-titre">Le module Marketing (campagnes promotionnelles, listes de diffusion) arrive après le pilote du Recouvrement.</p>
      </div></div>
      <div class="info"><i class="pi pi-info-circle"></i><span>Votre compte y est déjà habilité : les écrans apparaîtront ici dès leur mise en service.</span></div>
      @if (auth.recouvrement()) { <a routerLink="/">Revenir au Recouvrement</a> }
    </div>`
})
export class Marketing {
  protected readonly auth = inject(Auth);
}
