import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AideService, ChampEnErreur } from './aide';

/** Saisies refusees par le serveur : transmises au guidage, qui les signale sur les champs de l'ecran. */
export const intercepteurGuidage: HttpInterceptorFn = (requete, suivant) => {
  const aide = inject(AideService);
  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const champs = erreur.status === 400 && Array.isArray(erreur.error?.champs) ? erreur.error.champs as ChampEnErreur[] : [];
      if (champs.length) {
        aide.erreursSaisie.set(champs);
      }
      return throwError(() => erreur);
    })
  );
};
