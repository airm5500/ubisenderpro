import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../core/auth';
import { CharteService } from '../core/charte';
import { ModuleCourant } from '../core/module-courant';
import { Guidage } from './guidage';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Guidage],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  protected readonly auth = inject(Auth);
  protected readonly charte = inject(CharteService);
  protected readonly nomEntreprise = 'UBIPHARM CI';
  /** Lettres du nom, pour l'apparition lettre a lettre du theme B. */
  protected readonly lettres = [...this.nomEntreprise].map(c => c === ' ' ? '\u00a0' : c);
  protected readonly module = inject(ModuleCourant);

  /** Module affiche : celui choisi ou d'ou l'on vient ; un utilisateur du seul Marketing reste en Marketing. */
  protected readonly surMarketing = computed(() => !this.auth.recouvrement() || this.module.courant() === 'MARKETING');
}
