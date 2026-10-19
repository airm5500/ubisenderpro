import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../core/auth';
import { CharteService } from '../core/charte';
import { ModuleCourant } from '../core/module-courant';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  protected readonly auth = inject(Auth);
  protected readonly charte = inject(CharteService);
  protected readonly module = inject(ModuleCourant);

  /** Module affiche : celui choisi ou d'ou l'on vient ; un utilisateur du seul Marketing reste en Marketing. */
  protected readonly surMarketing = computed(() => !this.auth.recouvrement() || this.module.courant() === 'MARKETING');
}
