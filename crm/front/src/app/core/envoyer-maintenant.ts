import { Component, inject, input, output, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';
import { Api } from './api';

/** Issue d'un « Envoyer maintenant » : le statut du message apres l'essai. */
export interface EnvoiImmediat { cle: string; statut: string; motif: string | null; }

/**
 * Bouton « Envoyer maintenant » d'une ligne de message : actif tant que le message attend son tour, grise des qu'il
 * est parti (ou sorti de l'attente). cle : C + id d'un message de campagne, S + id d'un message d'envoi en masse.
 */
@Component({
  selector: 'app-envoyer-maintenant',
  imports: [TooltipModule],
  template: `
    <button type="button" class="bi" [disabled]="statut() !== 'A_ENVOYER' || !cle() || encours()"
            [pTooltip]="statut() === 'A_ENVOYER' ? 'Envoyer maintenant' : 'Déjà traité'" tooltipPosition="top"
            aria-label="Envoyer maintenant" (click)="$event.stopPropagation(); envoyer()">
      <i class="pi" [class.pi-send]="!encours()" [class.pi-spin]="encours()" [class.pi-spinner]="encours()"></i>
    </button>`
})
export class EnvoyerMaintenant {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  readonly cle = input<string | null>(null);
  readonly statut = input<string>('');
  readonly envoye = output<EnvoiImmediat>();
  protected readonly encours = signal(false);

  envoyer(): void {
    this.encours.set(true);
    this.api.post<EnvoiImmediat>(`/api/envois/${this.cle()}/immediat`).subscribe({
      next: r => {
        this.encours.set(false);
        this.messages.add(r.statut === 'ECHEC'
          ? { severity: 'error', summary: 'Envoi échoué', detail: r.motif ?? '' }
          : { severity: 'success', summary: 'Message envoyé' });
        this.envoye.emit(r);
      },
      error: () => this.encours.set(false)
    });
  }
}
