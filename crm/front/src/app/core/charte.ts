import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Charte {
  nom: string; couleurPrincipale: string; couleurSecondaire: string; theme: string; couleurFond: string; couleurSurvol: string; logoVersion: number | null; logoNom: string | null;
  iconeVersion: number | null; iconeNom: string | null; banniereVersion: number | null; banniereNom: string | null; siteWeb: string | null; canalDefaut: string; modeEnvoiDefaut: string;
}

/** Nom, couleurs, logo et icone choisis dans les Parametres ; lus avant la connexion et appliques a toute l'application. */
@Injectable({ providedIn: 'root' })
export class CharteService {
  private readonly http = inject(HttpClient);

  readonly charte = signal<Charte | null>(null);

  charger(): void {
    this.http.get<Charte>('/api/charte').subscribe(c => this.appliquer(c));
  }

  /** Adresse du logo, changee a chaque depot pour ne pas garder l'ancien en cache. */
  logo(): string {
    const v = this.charte()?.logoVersion;
    return v ? `/api/charte/logo?v=${v}` : 'logo.svg';
  }

  appliquer(c: Charte): void {
    this.charte.set(c);
    const racine = document.documentElement.style;
    racine.setProperty('--bleu', c.couleurPrincipale);
    racine.setProperty('--vert', c.couleurSecondaire);
    if (c.couleurFond) racine.setProperty('--fond-page', c.couleurFond);
    if (c.couleurSurvol) racine.setProperty('--survol', c.couleurSurvol);
    document.documentElement.dataset['charte'] = ['A', 'B', 'C'].includes(c.theme) ? c.theme : 'A';
    document.title = c.nom;
    let icone = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!icone) {
      icone = document.createElement('link');
      icone.rel = 'icon';
      document.head.appendChild(icone);
    }
    icone.removeAttribute('type');
    icone.href = c.iconeVersion ? `/api/charte/icone?v=${c.iconeVersion}` : 'logo.svg';
  }
}
