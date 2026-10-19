import { Component, computed, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { ActionJournal, CompteUtilisateur, ConnexionHistorique, Page } from '../../core/modeles';
import { JourPipe } from '../../core/format';

/** Historique d'un compte : ses connexions, reussies ou non, et le journal de ses actions. */
@Component({
  selector: 'app-historique-utilisateur',
  imports: [FormsModule, DialogModule, JourPipe],
  templateUrl: './historique-utilisateur.html'
})
export class HistoriqueUtilisateur {
  private readonly api = inject(Api);

  readonly ouvert = model(false);
  readonly utilisateur = input<CompteUtilisateur | null>(null);

  protected readonly vue = signal<'connexions' | 'actions'>('connexions');
  protected readonly connexions = signal<Page<ConnexionHistorique> | null>(null);
  protected readonly actions = signal<Page<ActionJournal> | null>(null);
  criteres = { du: '', au: '', action: '', succes: '' };
  private numeroPage = 0;
  private readonly taille = 25;
  protected readonly page = computed(() => this.vue() === 'connexions' ? this.connexions() : this.actions());
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  preparer(): void {
    this.criteres = { du: '', au: '', action: '', succes: '' };
    this.vue.set('connexions');
    this.rechercher();
  }

  afficher(vue: 'connexions' | 'actions'): void {
    this.vue.set(vue);
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  protected get numero(): number { return this.numeroPage; }

  private charger(): void {
    const u = this.utilisateur();
    if (!u) return;
    const c = this.criteres;
    const communs = { du: c.du || null, au: c.au || null, page: this.numeroPage, taille: this.taille };
    if (this.vue() === 'connexions') {
      this.api.get<Page<ConnexionHistorique>>(`/api/utilisateurs/${u.id}/connexions`, { ...communs, succes: c.succes || null })
        .subscribe(p => this.connexions.set(p));
    } else {
      this.api.get<Page<ActionJournal>>(`/api/utilisateurs/${u.id}/actions`, { ...communs, action: c.action || null })
        .subscribe(p => this.actions.set(p));
    }
  }
}
