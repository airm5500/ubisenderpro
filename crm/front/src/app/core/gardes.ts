import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

// inject() doit etre appele avant le premier await : apres, on n'est plus dans le contexte d'injection

export const connecte: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return (await auth.verifier()) ? true : router.createUrlTree(['/connexion']);
};

/** Ecran ouvert a qui a l'un de ces droits ; les autres sont renvoyes vers leur accueil. */
export const droit = (...codes: string[]): CanActivateFn => async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  return auth.peut(...codes) ? true : router.createUrlTree([auth.accueil()]);
};

export const accueil: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  return router.createUrlTree([auth.utilisateur() ? auth.accueil() : '/connexion']);
};

/** Un compte sans le module Recouvrement ne voit que l'espace Marketing. */
export const moduleRecouvrement: CanActivateFn = async (_route, etat) => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  const libre = ['/marketing', '/mot-de-passe', '/utilisateurs', '/roles', '/fournisseurs-sms', '/parametres'].some(u => etat.url.startsWith(u));
  return auth.recouvrement() || libre ? true : router.createUrlTree(['/marketing']);
};

/** Mot de passe provisoire (compte cree ou reinitialise par l'administrateur) : a remplacer avant tout autre ecran. */
export const motDePasseAJour: CanActivateFn = async (_route, etat) => {
  const auth = inject(Auth);
  const router = inject(Router);
  await auth.verifier();
  return !auth.utilisateur()?.motDePasseProvisoire || etat.url.startsWith('/mot-de-passe') ? true : router.createUrlTree(['/mot-de-passe']);
};
