import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Auth } from '../core/auth';
import { CharteService } from '../core/charte';
import { ModuleCourant } from '../core/module-courant';
import { apresClic, sectionOuverte } from '../core/menu-sections';
import { Guidage } from './guidage';
import { FenetreCampagne } from '../core/fenetre-campagne';
import { FenetreCampagneComponent } from '../pages/campagne-edition/fenetre-campagne';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Guidage, FenetreCampagneComponent],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
  host: { '[class.repliable]': 'repliable()' }
})
export class Shell {
  protected readonly fenetreCampagne = inject(FenetreCampagne);
  protected readonly auth = inject(Auth);
  protected readonly charte = inject(CharteService);
  /** Nom de l'entreprise choisi dans Parametres > Charte. */
  protected readonly nomEntreprise = computed(() => this.charte.charte()?.nomEntreprise || 'UBIPHARM CI');
  /** Lettres du nom, pour l'apparition lettre a lettre du theme B. */
  protected readonly lettres = computed(() => [...this.nomEntreprise()].map(c => c === ' ' ? '\u00a0' : c));
  protected readonly module = inject(ModuleCourant);

  /** Module affiche : celui choisi ou d'ou l'on vient ; un utilisateur du seul Marketing reste en Marketing. */
  protected readonly surMarketing = computed(() => !this.auth.recouvrement() || this.module.courant() === 'MARKETING');

  /** Sections du menu repliables (Parametres > Charte) : une seule ouverte a la fois. */
  protected readonly repliable = computed(() => this.charte.charte()?.menuRepliable === true);
  /** Section ouverte par un clic (voir menu-sections). */
  private readonly ouverte = signal<string | null>(null);

  private readonly menuDefile = viewChild<ElementRef<HTMLElement>>('menuDefile');
  private readonly menuContenu = viewChild<ElementRef<HTMLElement>>('menuContenu');
  /** Vrai tant qu'il reste du menu sous la partie visible : le fondu et la fleche s'affichent. */
  protected readonly suiteMenu = signal(false);

  constructor() {
    // Le menu change de hauteur quand une section s'ouvre, que la fenetre change de taille ou que les droits arrivent
    const destruction = inject(DestroyRef);
    afterNextRender(() => {
      const observateur = new ResizeObserver(() => this.mesurerMenu());
      for (const el of [this.menuDefile(), this.menuContenu()]) if (el) observateur.observe(el.nativeElement);
      destruction.onDestroy(() => observateur.disconnect());
      this.mesurerMenu();
    });
    // Apres chaque navigation, la section de l'ecran affiche redevient celle qui est ouverte
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => this.ouverte.set(null));
  }

  protected mesurerMenu(): void {
    const m = this.menuDefile()?.nativeElement;
    this.suiteMenu.set(!!m && m.scrollTop + m.clientHeight < m.scrollHeight - 2);
  }

  /** La fleche fait descendre le menu d'une demi-hauteur. */
  protected defilerMenu(): void {
    const m = this.menuDefile()?.nativeElement;
    m?.scrollBy({ top: m.clientHeight * 0.6, behavior: 'smooth' });
  }

  protected estOuverte(cle: string, active: boolean): boolean {
    return sectionOuverte(this.repliable(), this.ouverte(), cle, active);
  }

  protected basculer(cle: string, active: boolean): void {
    this.ouverte.set(apresClic(this.repliable(), this.ouverte(), cle, active));
  }
}
