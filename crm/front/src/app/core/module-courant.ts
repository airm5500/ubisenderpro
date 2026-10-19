import { Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

export type Module = 'RECOUVREMENT' | 'MARKETING';

/** Ecrans communs aux deux modules : ils gardent le module d'ou l'on vient au lieu de ramener au Recouvrement. */
const COMMUNS = ['/discussions', '/envoi-masse', '/historique-envois', '/support', '/utilisateurs', '/roles', '/parametres',
  '/agences', '/fournisseurs-sms', '/clients', '/journal', '/mot-de-passe'];

const CLE = 'crm.module';

/** Module affiche dans le menu (Recouvrement ou Marketing), garde le temps de la session du navigateur. */
@Injectable({ providedIn: 'root' })
export class ModuleCourant {
  private readonly router = inject(Router);

  readonly courant = signal<Module>(ModuleCourant.lire());

  constructor() {
    this.suivre(this.router.url);
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(e => this.suivre((e as NavigationEnd).urlAfterRedirects));
  }

  choisir(module: Module): void {
    this.courant.set(module);
    try { sessionStorage.setItem(CLE, module); } catch { /* stockage indisponible : le module vaut pour cette page */ }
  }

  private suivre(url: string): void {
    const chemin = url.split(/[?#]/)[0];
    if (chemin.startsWith('/marketing')) {
      this.choisir('MARKETING');
    } else if (chemin !== '/' && chemin !== '/connexion' && !COMMUNS.some(c => chemin === c || chemin.startsWith(c + '/'))) {
      this.choisir('RECOUVREMENT');
    }
  }

  private static lire(): Module {
    try { return sessionStorage.getItem(CLE) === 'MARKETING' ? 'MARKETING' : 'RECOUVREMENT'; } catch { return 'RECOUVREMENT'; }
  }
}
