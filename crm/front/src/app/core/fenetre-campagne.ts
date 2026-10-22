import { Injectable, signal } from '@angular/core';
import { Campagne } from './modeles';

/**
 * Creation et modification d'une campagne de relance en fenetre, par-dessus l'ecran en cours (regle d'Airman du 22/10/2026).
 * N'importe quel ecran l'ouvre ; la fenetre elle-meme est posee une fois dans le cadre de l'application.
 */
@Injectable({ providedIn: 'root' })
export class FenetreCampagne {
  /** id : brouillon a modifier ; clients : relance de clients choisis (identifiants separes par des virgules). */
  readonly demande = signal<{ id?: number; clients?: string } | null>(null);
  /** Derniere campagne enregistree depuis la fenetre : l'ecran de suivi se recharge s'il l'affiche. */
  readonly enregistree = signal<Campagne | null>(null);

  ouvrir(demande: { id?: number; clients?: string } = {}): void {
    this.demande.set(demande);
  }

  fermer(): void {
    this.demande.set(null);
  }
}
