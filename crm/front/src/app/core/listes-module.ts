import { inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/** Ou vivent les listes de diffusion d'un module (Q7 du 22/10/2026) : chacun les siennes, jamais celles de l'autre. */
export interface EspaceListes { marketing: boolean; api: string; ecran: string; droit: string; droitGerer: string; }

const MARKETING: EspaceListes = { marketing: true, api: '/api/marketing/listes', ecran: '/marketing/listes', droit: 'LISTES', droitGerer: 'LISTES_GERER' };
const RELANCES: EspaceListes = { marketing: false, api: '/api/recouvrement/listes', ecran: '/relances/listes', droit: 'LISTES_RELANCES', droitGerer: 'LISTES_RELANCES_GERER' };

/** Espace des listes de l'ecran ouvert, lu dans les donnees de sa route (module RECOUVREMENT pour les relances). */
export function espaceListes(): EspaceListes {
  return inject(ActivatedRoute).snapshot.data['module'] === 'RECOUVREMENT' ? RELANCES : MARKETING;
}
