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
  /** ADMIN et Direction du Recouvrement : validation des campagnes, modeles, interims, numeros. */
  readonly gestionnaire = computed(() => this.aUnRole('ADMIN', 'DIR_RECOUVREMENT'));
  /** Profils de direction (imports, affectations) ; une Direction d'agence est limitee a ses agences. */
  readonly direction = computed(() => this.aUnRole('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE'));
  /** Consultation des agents et portefeuilles. */
  readonly pilotage = computed(() => this.aUnRole('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE', 'DG', 'DIR_COMMERCIAL'));
  readonly agent = computed(() => this.aUnRole('AGENT'));
  readonly auteurCampagnes = computed(() => this.aUnRole('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE', 'AGENT'));

  aUnRole(...roles: string[]): boolean {
    return this.roles().some(r => roles.includes(r));
  }

  /** Libelle du profil affiche en bas du menu. */
  readonly libelleProfil = computed(() => {
    const u = this.utilisateur();
    if (!u) return '';
    const libelles: Record<string, string> = {
      ADMIN: 'Administrateur', DIR_RECOUVREMENT: 'Direction du Recouvrement', DIR_AGENCE: "Direction d'agence",
      AGENT: 'Agent de recouvrement', DG: 'Directeur Général', DIR_COMMERCIAL: 'Directeur Commercial'
    };
    const profil = u.roles.map(r => libelles[r] ?? r).join(', ');
    return u.visionGlobale ? `${profil} · toutes agences` : profil;
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

  sessionTerminee(): void {
    this.utilisateur.set(null);
    this.router.navigate(['/connexion']);
  }

  /** Page d'accueil selon le profil. */
  accueil(): string {
    return this.agent() && !this.direction() ? '/ma-journee' : '/factures';
  }

  private jetonCsrf(): Observable<unknown> {
    return this.http.get('/api/auth/csrf').pipe(catchError(() => of(null)));
  }
}
