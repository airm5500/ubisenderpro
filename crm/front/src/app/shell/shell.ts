import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Auth } from '../core/auth';
import { CharteService } from '../core/charte';
import { ModuleCourant } from '../core/module-courant';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
  host: { '[class.repliable]': 'repliable()' }
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

  /** Sections du menu repliables (Parametres > Charte) : une seule ouverte a la fois. */
  protected readonly repliable = computed(() => this.charte.charte()?.menuRepliable === true);
  /** Section ouverte par un clic ; null = celle de l'ecran affiche, '' = aucune. */
  private readonly ouverte = signal<string | null>(null);

  constructor() {
    // Apres chaque navigation, la section de l'ecran affiche redevient celle qui est ouverte
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => this.ouverte.set(null));
  }

  protected estOuverte(cle: string, active: boolean): boolean {
    if (!this.repliable()) return true;
    const o = this.ouverte();
    return o === null ? active : o === cle;
  }

  protected basculer(cle: string, active: boolean): void {
    if (this.repliable()) this.ouverte.set(this.estOuverte(cle, active) ? '' : cle);
  }
}
