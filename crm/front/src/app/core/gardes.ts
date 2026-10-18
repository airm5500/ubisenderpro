import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

// inject() doit etre appele avant le premier await : apres, on n'est plus dans le contexte d'injection

export const connecte: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return (await auth.verifier()) ? true : router.createUrlTree(['/connexion']);
};

/** Ecran reserve a certains roles ; les autres sont renvoyes vers leur accueil. */
export const roles = (...autorises: string[]): CanActivateFn => async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  return auth.aUnRole(...autorises) ? true : router.createUrlTree([auth.accueil()]);
};

export const accueil: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  return router.createUrlTree([auth.utilisateur() ? auth.accueil() : '/connexion']);
};
