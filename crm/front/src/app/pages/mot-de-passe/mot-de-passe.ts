import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { messageErreur } from '../../core/intercepteur';

@Component({
  selector: 'app-mot-de-passe',
  imports: [FormsModule],
  templateUrl: './mot-de-passe.html'
})
export class MotDePasse {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);

  actuel = '';
  protected readonly nouveau = signal('');
  confirmation = '';
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);
  protected readonly provisoire = computed(() => this.auth.utilisateur()?.motDePasseProvisoire ?? false);

  // Memes regles que le serveur, pour guider la saisie avant l'envoi
  protected readonly regles = computed(() => {
    const n = this.nouveau();
    const login = this.auth.utilisateur()?.login.toLowerCase() ?? '';
    return [
      { libelle: 'Au moins 10 caractères', ok: n.length >= 10 },
      { libelle: 'Des lettres et des chiffres', ok: /\p{L}/u.test(n) && /\d/.test(n) },
      { libelle: 'Sans votre identifiant', ok: n.length > 0 && !n.toLowerCase().includes(login) }
    ];
  });

  enregistrer(): void {
    if (!this.actuel || !this.nouveau()) {
      this.erreur.set('Saisissez votre mot de passe actuel et le nouveau.');
      return;
    }
    if (this.nouveau() !== this.confirmation) {
      this.erreur.set('La confirmation ne correspond pas au nouveau mot de passe.');
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.api.postSilencieux('/api/auth/mot-de-passe', { actuel: this.actuel, nouveau: this.nouveau() }).subscribe({
      next: () => {
        this.auth.motDePasseRemplace();
        this.messages.add({ severity: 'success', summary: 'Mot de passe enregistré', detail: 'Utilisez-le à votre prochaine connexion.' });
        this.router.navigateByUrl(this.auth.accueil());
      },
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreur.set(messageErreur(e));
      }
    });
  }
}
