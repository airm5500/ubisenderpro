import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router } from '@angular/router';
import { finalize, tap } from 'rxjs';
import { Auth } from './auth';

const URL_ENVOI = '/api/support/rum';
/** Adresses qui ne se mesurent pas : l'envoi des mesures et la remontee des erreurs. */
const NON_MESURES = [URL_ENVOI, '/api/support/erreurs/navigateur'];
const ENVOI_MS = 60000;
/** Un ecran est ouvert quand plus aucun appel n'est en cours depuis 400 ms ; au-dela de 60 s, l'ouverture compte en echec. */
const CALME_MS = 400;
const OUVERTURE_MAX_MS = 60000;
const MAX_CLES = 300;
const MAX_ACTIONS = 15;

interface Cumul { type: 'ECRAN' | 'API'; cle: string; nombre: number; totalMs: number; maxMs: number; nb1s: number; nb3s: number; echecs: number; }

/**
 * Experience utilisateur (RUM) reprise de Prestige : duree de chaque appel a l'API et temps d'ouverture de chaque ecran, cumules dans
 * le navigateur et envoyes une fois par minute (et a la mise en arriere-plan de l'onglet), par un fetch hors HttpClient pour ne pas
 * se mesurer lui-meme. Aucun contenu : seulement l'adresse sans parametres, le titre de l'ecran et des durees.
 * Garde aussi les 15 dernieres actions (ecrans, appels) jointes aux erreurs remontees au diagnostic.
 */
@Injectable({ providedIn: 'root' })
export class Mesures {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  private cumuls = new Map<string, Cumul>();
  private readonly actions: string[] = [];
  private actif = true;
  private demarre = false;
  private enCours = 0;
  private ecran: { cle: string; debut: number } | null = null;
  private debutNavigation = 0;
  private minuterie: ReturnType<typeof setTimeout> | null = null;

  demarrer(): void {
    if (this.demarre || typeof window === 'undefined') return;
    this.demarre = true;
    this.router.events.subscribe(e => {
      if (e instanceof NavigationStart) {
        this.debutNavigation = performance.now();
      } else if (e instanceof NavigationEnd) {
        const titre = titreEcran(this.router.routerState.snapshot.root);
        this.action(`Écran ${titre ?? e.urlAfterRedirects.split('?')[0]}`);
        this.ecran = titre ? { cle: titre, debut: this.debutNavigation || performance.now() } : null;
        this.attendreCalme();
      } else if (e instanceof NavigationCancel || e instanceof NavigationError) {
        this.ecran = null;
      }
    });
    setInterval(() => this.envoyer(false), ENVOI_MS);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') this.envoyer(true);
    });
  }

  /** Appel a l'API commence (pour reperer la fin de l'ouverture d'un ecran). */
  debutAppel(): void {
    this.enCours++;
    if (this.minuterie) {
      clearTimeout(this.minuterie);
      this.minuterie = null;
    }
  }

  /** statut null : appel abandonne par l'ecran (changement de page), ni mesure ni echec. */
  finAppel(methode: string, url: string, dureeMs: number, statut: number | null): void {
    this.enCours = Math.max(0, this.enCours - 1);
    if (statut === null) {
      this.attendreCalme();
      return;
    }
    const chemin = cheminSeul(url);
    const echec = statut === 0 || statut >= 400;
    this.ajouter('API', `${methode} ${chemin}`, dureeMs, echec);
    this.action(`${methode} ${chemin} ${statut || 'sans réponse'} en ${Math.round(dureeMs)} ms`);
    this.attendreCalme();
  }

  /** Dernieres actions de l'utilisateur, de la plus ancienne a la plus recente. */
  dernieresActions(): string[] {
    return [...this.actions];
  }

  private attendreCalme(): void {
    if (!this.ecran || this.enCours > 0 || this.minuterie) return;
    this.minuterie = setTimeout(() => {
      this.minuterie = null;
      const e = this.ecran;
      if (!e || this.enCours > 0) return;
      this.ecran = null;
      const duree = performance.now() - CALME_MS - e.debut;
      this.ajouter('ECRAN', e.cle, Math.min(duree, OUVERTURE_MAX_MS), duree > OUVERTURE_MAX_MS);
    }, CALME_MS);
  }

  private ajouter(type: 'ECRAN' | 'API', cle: string, dureeMs: number, echec: boolean): void {
    if (!this.actif || !this.auth.utilisateur()) return;
    const k = `${type}|${cle}`;
    let c = this.cumuls.get(k);
    if (!c) {
      if (this.cumuls.size >= MAX_CLES) return;
      c = { type, cle, nombre: 0, totalMs: 0, maxMs: 0, nb1s: 0, nb3s: 0, echecs: 0 };
      this.cumuls.set(k, c);
    }
    const ms = Math.max(0, Math.round(dureeMs));
    c.nombre++;
    c.totalMs += ms;
    c.maxMs = Math.max(c.maxMs, ms);
    if (ms >= 1000) c.nb1s++;
    if (ms >= 3000) c.nb3s++;
    if (echec) c.echecs++;
  }

  private action(texte: string): void {
    const heure = new Date().toLocaleTimeString('fr-FR');
    this.actions.push(`${heure} ${texte}`);
    if (this.actions.length > MAX_ACTIONS) this.actions.shift();
  }

  private envoyer(fermeture: boolean): void {
    if (!this.cumuls.size || !this.auth.utilisateur()) return;
    const lot = [...this.cumuls.values()];
    this.cumuls = new Map();
    try {
      // keepalive : l'envoi aboutit meme si l'onglet se ferme ; le jeton CSRF part dans l'en-tete comme pour HttpClient
      fetch(URL_ENVOI, {
        method: 'POST', credentials: 'same-origin', keepalive: fermeture,
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': jetonCsrf() },
        body: JSON.stringify(lot)
      }).then(r => r.ok ? r.json() : null).then(r => {
        if (r && r.actif === false) this.actif = false;
      }).catch(() => undefined);
    } catch {
      // la mesure ne doit jamais gener l'utilisateur
    }
  }
}

/** Mesure chaque appel HttpClient a l'API (hors envoi des mesures). */
export const intercepteurMesures: HttpInterceptorFn = (requete, suivant) => {
  if (!requete.url.startsWith('/api/') || NON_MESURES.some(u => requete.url.startsWith(u))) return suivant(requete);
  const mesures = inject(Mesures);
  const debut = performance.now();
  let statut: number | null = null;
  mesures.debutAppel();
  return suivant(requete).pipe(
    tap({
      next: e => { if (e instanceof HttpResponse) statut = e.status; },
      error: (e: unknown) => { statut = e instanceof HttpErrorResponse ? e.status : 0; }
    }),
    finalize(() => mesures.finAppel(requete.method, requete.urlWithParams, performance.now() - debut, statut))
  );
};

/** Titre de la route la plus profonde (celui de l'onglet du navigateur). */
function titreEcran(racine: ActivatedRouteSnapshot): string | null {
  let route: ActivatedRouteSnapshot | null = racine;
  let titre: string | null = null;
  while (route) {
    if (typeof route.title === 'string' && route.title) titre = route.title;
    route = route.firstChild;
  }
  return titre;
}

/** Adresse sans parametres ; les identifiants numeriques deviennent {id} (le serveur renormalise de toute facon). */
function cheminSeul(url: string): string {
  return url.split('?')[0].split('#')[0].replace(/\/\d+(?=\/|$)/g, '/{id}');
}

function jetonCsrf(): string {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : '';
}
