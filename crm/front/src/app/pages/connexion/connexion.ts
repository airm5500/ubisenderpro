import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Auth } from '../../core/auth';
import { messageErreur } from '../../core/intercepteur';

const CLE_IDENTIFIANT = 'crm.identifiant';

@Component({
  selector: 'app-connexion',
  imports: [FormsModule],
  templateUrl: './connexion.html',
  styleUrl: './connexion.css'
})
export class Connexion {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  login = lire(CLE_IDENTIFIANT);
  motDePasse = '';
  seSouvenir = this.login !== '';
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);
  protected readonly aideMotDePasse = signal(false);

  connecter(): void {
    if (!this.login || !this.motDePasse) {
      this.erreur.set('Saisissez votre identifiant et votre mot de passe.');
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.auth.connecter(this.login.trim(), this.motDePasse).subscribe({
      next: () => {
        ecrire(CLE_IDENTIFIANT, this.seSouvenir ? this.login.trim() : '');
        // Si la session est refusee aussitot (page d'accueil en 401), on reste ici : le bouton redevient utilisable
        this.router.navigateByUrl(this.auth.accueil()).finally(() => this.enCours.set(false));
      },
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        this.motDePasse = '';
        this.erreur.set(e.status === 401 && !e.error?.message ? 'Identifiant ou mot de passe incorrect.' : messageErreur(e));
      }
    });
  }
}

function lire(cle: string): string {
  try { return localStorage.getItem(cle) ?? ''; } catch { return ''; }
}

function ecrire(cle: string, valeur: string): void {
  try { valeur ? localStorage.setItem(cle, valeur) : localStorage.removeItem(cle); } catch { /* stockage indisponible */ }
}
