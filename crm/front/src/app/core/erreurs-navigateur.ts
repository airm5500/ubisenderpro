import { ErrorHandler, Injectable, Injector, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Api } from './api';
import { Auth } from './auth';
import { Mesures } from './mesures';

/** Au plus 5 erreurs remontees par minute depuis un meme navigateur. */
const PLAFOND_MINUTE = 5;

/**
 * Erreurs JavaScript inattendues : affichees dans la console comme d'habitude, et remontees au diagnostic du centre de support.
 * Best-effort : la remontee ne leve jamais d'exception et ne notifie pas l'utilisateur.
 */
@Injectable()
export class ErreursNavigateur implements ErrorHandler {
  // Api et Auth sont lus a la demande : le gestionnaire d'erreurs est cree avant eux
  private readonly injecteur = inject(Injector);
  private debutFenetre = 0;
  private nbFenetre = 0;

  handleError(erreur: unknown): void {
    console.error(erreur);
    try {
      this.remonter(erreur);
    } catch {
      // la remontee ne doit jamais aggraver l'erreur
    }
  }

  private remonter(erreur: unknown): void {
    // Les erreurs HTTP sont deja tracees par le serveur
    if (erreur instanceof HttpErrorResponse) return;
    if (!this.injecteur.get(Auth).utilisateur()) return;
    const maintenant = Date.now();
    if (maintenant - this.debutFenetre >= 60000) {
      this.debutFenetre = maintenant;
      this.nbFenetre = 0;
    }
    if (++this.nbFenetre > PLAFOND_MINUTE) return;
    const e = erreur instanceof Error ? erreur : new Error(String(erreur));
    // Fil d'Ariane : ce que l'utilisateur venait de faire
    const actions = this.injecteur.get(Mesures).dernieresActions();
    const pile = (e.stack ?? '') + (actions.length ? '\n\nDernières actions :\n' + actions.join('\n') : '');
    const corps = {
      type: (e.name || 'Error').substring(0, 255),
      message: (e.message || String(erreur)).substring(0, 2000),
      pile: pile.substring(0, 20000),
      url: location.pathname.substring(0, 500)
    };
    this.injecteur.get(Api).postSilencieux('/api/support/erreurs/navigateur', corps).subscribe({ error: () => undefined });
  }
}
