import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, map, of, switchMap, tap } from 'rxjs';
import { Utilisateur } from './modeles';

/** Session serveur (cookie HttpOnly) : le front ne garde que le profil renvoye par /api/auth/moi. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly utilisateur = signal<Utilisateur | null>(null);
  private verifie = false;

  readonly roles = computed(() => this.utilisateur()?.roles ?? []);
  /** Ecrans et actions coches sur ses roles (ecran Roles et droits) ; le serveur applique les memes. */
  readonly droits = computed(() => new Set(this.utilisateur()?.droits ?? []));
  /** Liste des agents (filtres, portefeuilles) : directions et consultation. */
  readonly voitAgents = computed(() => this.peut('PORTEFEUILLES', 'PERIMETRE_AGENCES'));
  /** Sans droit sur son agence, l'utilisateur ne voit que son portefeuille (agent). */
  readonly portefeuillePersonnel = computed(() => !this.peut('PERIMETRE_AGENCES'));

  readonly modules = computed(() => this.utilisateur()?.modules ?? []);
  readonly recouvrement = computed(() => this.modules().includes('RECOUVREMENT'));
  readonly marketing = computed(() => this.modules().includes('MARKETING'));

  /** Vrai si l'un des droits est coche sur ses roles. */
  peut(...codes: string[]): boolean {
    const droits = this.droits();
    return codes.some(c => droits.has(c));
  }

  /** Libelle du profil affiche en bas du menu. */
  readonly libelleProfil = computed(() => {
    const u = this.utilisateur();
    if (!u) return '';
    return u.visionGlobale ? `${u.profil} · toutes agences` : u.profil;
  });

  /** Au demarrage : recupere le jeton CSRF puis la session en cours, si elle existe. */
  async verifier(): Promise<boolean> {
    if (this.verifie) return this.utilisateur() !== null;
    this.verifie = true;
    const u = await firstValueFrom(this.jetonCsrf().pipe(
      switchMap(() => this.http.get<Utilisateur>('/api/auth/moi')),
      catchError(() => of(null))
    ));
    this.utilisateur.set(u);
    return u !== null;
  }

  connecter(login: string, motDePasse: string): Observable<Utilisateur> {
    return this.jetonCsrf().pipe(
      switchMap(() => this.http.post<Utilisateur>('/api/auth/connexion', { login, motDePasse })),
      // La connexion renouvelle la session : on reprend un jeton CSRF neuf pour les ecritures suivantes
      switchMap(u => this.jetonCsrf().pipe(map(() => u))),
      tap(u => this.utilisateur.set(u))
    );
  }

  deconnecter(): void {
    this.http.post('/api/auth/deconnexion', {}).pipe(catchError(() => of(null))).subscribe(() => this.sessionTerminee());
  }

  /** Apres le remplacement du mot de passe provisoire, les autres ecrans s'ouvrent. */
  motDePasseRemplace(): void {
    const u = this.utilisateur();
    if (u) this.utilisateur.set({ ...u, motDePasseProvisoire: false });
  }

  sessionTerminee(): void {
    this.utilisateur.set(null);
    this.router.navigate(['/connexion']);
  }

  /** Page d'accueil selon le profil. */
  accueil(): string {
    if (this.utilisateur()?.motDePasseProvisoire) return '/mot-de-passe';
    if (!this.recouvrement()) return '/marketing';
    // Premier ecran permis, dans l'ordre du menu
    const ecrans: [string, string][] = [['MA_JOURNEE', '/ma-journee'], ['FACTURES', '/factures'], ['CLIENTS', '/clients'],
      ['PORTEFEUILLES', '/portefeuilles'], ['CAMPAGNES', '/campagnes'], ['MODELES', '/modeles'], ['REPONSES', '/reponses'],
      ['NUMEROS', '/numeros'], ['NUMEROS_TOUS', '/numeros'], ['UTILISATEURS', '/utilisateurs'], ['ROLES', '/roles'],
      ['PARAMETRES', '/parametres'], ['FOURNISSEURS_SMS', '/fournisseurs-sms']];
    return ecrans.find(([droit]) => this.peut(droit))?.[1] ?? '/mot-de-passe';
  }

  private jetonCsrf(): Observable<unknown> {
    return this.http.get('/api/auth/csrf').pipe(catchError(() => of(null)));
  }
}
