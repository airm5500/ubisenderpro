import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';
import { Page } from './modeles';
import { SILENCIEUX } from './intercepteur';

type Valeur = string | number | boolean | null | undefined;

/** Acces a l'API : les lectures en GET avec query params, les ecritures en POST/PUT (jeton CSRF ajoute par Angular). */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  get<T>(url: string, criteres: Record<string, Valeur> = {}): Observable<T> {
    return this.http.get<T>(url, { params: parametres(criteres) });
  }

  /** Lecture dont l'appelant gere l'echec lui-meme (pas de notification d'erreur). */
  getSilencieux<T>(url: string, criteres: Record<string, Valeur> = {}): Observable<T> {
    return this.http.get<T>(url, { params: parametres(criteres), context: new HttpContext().set(SILENCIEUX, true) });
  }

  /** Toutes les pages d'une recherche paginee (taille = maximum accepte par l'API), dans la limite de plafond elements. */
  toutes<T>(url: string, criteres: Record<string, Valeur>, taille: number, plafond = 5000): Observable<T[]> {
    const page = (n: number) => this.get<Page<T>>(url, { ...criteres, page: n, taille });
    return page(0).pipe(
      expand(p => (p.page + 1) * taille < Math.min(p.total, plafond) ? page(p.page + 1) : EMPTY),
      map(p => p.elements),
      reduce((tous, elements) => tous.concat(elements), [] as T[])
    );
  }

  post<T>(url: string, corps: unknown = {}, criteres: Record<string, Valeur> = {}): Observable<T> {
    return this.http.post<T>(url, corps, { params: parametres(criteres) });
  }

  /** Ecriture dont l'appelant affiche lui-meme l'erreur (formulaire). */
  postSilencieux<T>(url: string, corps: unknown = {}): Observable<T> {
    return this.http.post<T>(url, corps, { context: new HttpContext().set(SILENCIEUX, true) });
  }

  put<T>(url: string, corps: unknown): Observable<T> {
    return this.http.put<T>(url, corps);
  }

  envoyerFichier<T>(url: string, fichier: File, json?: { nom: string; valeur: unknown }, criteres: Record<string, Valeur> = {}): Observable<T> {
    const donnees = new FormData();
    donnees.append('fichier', fichier);
    if (json) {
      donnees.append(json.nom, new Blob([JSON.stringify(json.valeur)], { type: 'application/json' }));
    }
    return this.http.post<T>(url, donnees, { params: parametres(criteres) });
  }

  /** Telecharge un fichier renvoye par l'API (exemplaire, rejets) sous le nom donne par le serveur. */
  telecharger(url: string, nomParDefaut: string): void {
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe(reponse => {
      const disposition = reponse.headers.get('Content-Disposition') ?? '';
      const nom = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? nomParDefaut;
      const lien = document.createElement('a');
      lien.href = URL.createObjectURL(reponse.body!);
      lien.download = nom;
      lien.click();
      URL.revokeObjectURL(lien.href);
    });
  }
}

function parametres(criteres: Record<string, Valeur>): HttpParams {
  let p = new HttpParams();
  for (const [cle, valeur] of Object.entries(criteres)) {
    if (valeur !== null && valeur !== undefined && valeur !== '') {
      p = p.set(cle, String(valeur));
    }
  }
  return p;
}
