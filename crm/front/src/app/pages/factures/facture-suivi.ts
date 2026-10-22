import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Facture, SuiviFacture } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, aujourdhui } from '../../core/format';

/** Promesse de paiement, litige ou contentieux : la facture n'est plus relancee tant que le suivi tient. */
@Component({
  selector: 'app-facture-suivi',
  imports: [FormsModule, DialogModule, JourPipe, MontantPipe],
  templateUrl: './facture-suivi.html'
})
export class FactureSuivi {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;
  protected readonly demain = aujourdhui();

  readonly ouvert = model(false);
  readonly facture = input<Facture | null>(null);
  readonly enregistre = output<Facture>();

  protected readonly historique = signal<SuiviFacture[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire = { suivi: 'PROMESSE', datePromesse: '', montantPromis: null as number | null, commentaire: '' };

  preparer(): void {
    const f = this.facture();
    this.erreur.set(null);
    this.formulaire = { suivi: 'PROMESSE', datePromesse: '', montantPromis: null, commentaire: '' };
    this.historique.set([]);
    if (f) this.api.get<SuiviFacture[]>(`/api/recouvrement/factures/${f.id}/suivis`).subscribe(h => this.historique.set(h));
  }

  protected peutContentieux(): boolean {
    return this.auth.peut('FACTURES_CONTENTIEUX');
  }

  protected peutLever(): boolean {
    const f = this.facture();
    return !!f?.suivi && (f.suivi !== 'CONTENTIEUX' || this.peutContentieux());
  }

  protected pret(): boolean {
    const f = this.formulaire;
    return f.suivi === 'PROMESSE' ? !!f.datePromesse : !!f.commentaire.trim();
  }

  poser(): void {
    const f = this.formulaire;
    this.envoyer(f.suivi === 'PROMESSE'
      ? { suivi: f.suivi, datePromesse: f.datePromesse, montantPromis: f.montantPromis, commentaire: f.commentaire.trim() || null }
      : { suivi: f.suivi, commentaire: f.commentaire.trim() });
  }

  lever(): void {
    this.envoyer({ commentaire: this.formulaire.commentaire.trim() || null });
  }

  private envoyer(corps: unknown): void {
    const f = this.facture();
    if (!f) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<Facture>(`/api/recouvrement/factures/${f.id}/suivi`, corps).subscribe({
      next: facture => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: facture.suivi ? (LIBELLES.suiviFacture[facture.suivi] ?? facture.suivi) + ' enregistré' : 'Relances reprises',
          detail: `${facture.reference} · ${facture.clientRaisonSociale}` });
        this.enregistre.emit(facture);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
