import { Component, computed, input } from '@angular/core';

/** Jeu fixe de badges proposes pour un segment de clients (meme liste que le serveur). */
export const BADGES: Record<string, { icone: string; couleur: string; libelle: string }> = {
  COURONNE: { icone: 'pi-crown', couleur: '#8A6D00', libelle: 'Couronne' },
  TROPHEE: { icone: 'pi-trophy', couleur: '#A15C00', libelle: 'Trophée' },
  ETOILE: { icone: 'pi-star-fill', couleur: '#B7791F', libelle: 'Étoile' },
  MEDAILLE: { icone: 'pi-verified', couleur: '#5B6B7A', libelle: 'Médaille' },
  BOUCLIER: { icone: 'pi-shield', couleur: '#8B4513', libelle: 'Bouclier' },
  ECLAIR: { icone: 'pi-bolt', couleur: '#C2410C', libelle: 'Éclair' },
  COEUR: { icone: 'pi-heart-fill', couleur: '#BE185D', libelle: 'Cœur' },
  CADEAU: { icone: 'pi-gift', couleur: '#7B4BB7', libelle: 'Cadeau' },
  ETINCELLES: { icone: 'pi-sparkles', couleur: '#0369A1', libelle: 'Étincelles' },
  DRAPEAU: { icone: 'pi-flag-fill', couleur: '#157A47', libelle: 'Drapeau' },
  SIGNET: { icone: 'pi-bookmark-fill', couleur: '#0B5C8E', libelle: 'Signet' },
  SOLEIL: { icone: 'pi-sun', couleur: '#B45309', libelle: 'Soleil' }
};

export const CODES_BADGES = Object.keys(BADGES);

/** Pastille du segment : son badge et son nom. */
@Component({
  selector: 'app-badge-segment',
  template: `@if (libelle()) {
    <span class="badge-segment" [style.color]="b().couleur" [style.border-color]="b().couleur" [attr.title]="'Segment ' + libelle()">
      <i class="pi" [class]="b().icone"></i>{{ libelle() }}</span>
  }`
})
export class BadgeSegment {
  readonly badge = input<string | null | undefined>(null);
  readonly libelle = input<string | null | undefined>(null);
  protected readonly b = computed(() => BADGES[this.badge() ?? ''] ?? BADGES['ETOILE']);
}
