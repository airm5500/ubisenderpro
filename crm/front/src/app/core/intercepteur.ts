import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';
import { Auth } from './auth';

/** Requete dont l'ecran gere lui-meme l'echec : pas de notification. */
export const SILENCIEUX = new HttpContextToken<boolean>(() => false);

const SANS_MESSAGE = ['/api/auth/moi', '/api/auth/csrf', '/api/auth/connexion'];

/** Session expiree : retour a la connexion. Autre erreur : le message metier du serveur en notification. */
export const intercepteurErreurs: HttpInterceptorFn = (requete, suivant) => {
  const auth = inject(Auth);
  const messages = inject(MessageService);
  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const silencieux = requete.context.get(SILENCIEUX) || SANS_MESSAGE.some(u => requete.url.startsWith(u));
      if (erreur.status === 401 && !silencieux) {
        // Compte desactive ou droits changes par l'administrateur : le serveur explique pourquoi
        if (erreur.error?.message) {
          messages.add({ severity: 'info', summary: 'Session fermée', detail: erreur.error.message, life: 7000 });
        }
        auth.sessionTerminee();
      } else if (!silencieux) {
        messages.add({ severity: 'error', summary: 'Action impossible', detail: messageErreur(erreur), life: 7000 });
      }
      return throwError(() => erreur);
    })
  );
};

export function messageErreur(erreur: HttpErrorResponse): string {
  const corps = erreur.error;
  if (corps && typeof corps === 'object' && 'message' in corps) {
    const champs = Array.isArray(corps.champs) && corps.champs.length
      ? ' ' + corps.champs.map((c: { message: string }) => c.message).join(' ') : '';
    return corps.message + champs;
  }
  if (erreur.status === 0) return 'Serveur injoignable. Vérifiez votre connexion.';
  if (erreur.status === 403) return "Vous n'avez pas les droits pour cette action.";
  return `Erreur inattendue (${erreur.status}).`;
}
