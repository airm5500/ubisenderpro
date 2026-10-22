import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Promotion } from '../../core/modeles';

interface Formulaire { code: string; nom: string; description: string; dateDebut: string; dateFin: string; }

/** Creation ou modification d'une promotion : son code, son nom, sa periode. */
@Component({
  selector: 'app-promotion-formulaire',
  imports: [FormsModule, DialogModule],
  templateUrl: './promotion-formulaire.html'
})
export class PromotionFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  readonly ouvert = model(false);
  readonly promotion = input<Promotion | null>(null);
  readonly enregistre = output<Promotion>();

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = vide();

  preparer(): void {
    const p = this.promotion();
    this.erreur.set(null);
    this.formulaire = p ? { code: p.code, nom: p.nom, description: p.description ?? '', dateDebut: p.dateDebut, dateFin: p.dateFin } : vide();
  }

  enregistrer(): void {
    const p = this.promotion();
    const corps = { ...this.formulaire, code: this.formulaire.code.trim(), nom: this.formulaire.nom.trim() };
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = p ? this.api.putSilencieux<Promotion>(`/api/marketing/promotions/${p.id}`, corps)
      : this.api.postSilencieux<Promotion>('/api/marketing/promotions', corps);
    requete.subscribe({
      next: promotion => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: p ? 'Promotion enregistrée' : 'Promotion créée', detail: promotion.nom });
        this.enregistre.emit(promotion);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.champs?.[0]?.message ?? e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}

function vide(): Formulaire {
  const jour = new Date().toISOString().substring(0, 10);
  return { code: '', nom: '', description: '', dateDebut: jour, dateFin: '' };
}
