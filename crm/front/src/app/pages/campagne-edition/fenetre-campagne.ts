import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DialogModule } from 'primeng/dialog';
import { Campagne } from '../../core/modeles';
import { FenetreCampagne } from '../../core/fenetre-campagne';
import { CampagneEdition } from './campagne-edition';

/** Fenetre de saisie d'une campagne de relance, ouverte par FenetreCampagne depuis n'importe quel ecran. */
@Component({
  selector: 'app-fenetre-campagne',
  imports: [DialogModule, CampagneEdition],
  template: `
    <p-dialog [header]="fc.demande()?.id ? 'Modifier la campagne' : 'Nouvelle campagne'" [visible]="!!fc.demande()"
              (visibleChange)="!$event && fc.fermer()" [modal]="true" [draggable]="false" [maximizable]="true"
              [style]="{ width: '1320px', maxWidth: '97vw' }">
      @if (fc.demande(); as d) {
        <app-campagne-edition [fenetre]="true" [id]="d.id ? '' + d.id : undefined" [clients]="d.clients"
                              (fermer)="fc.fermer()" (enregistree)="enregistree($event)" />
      }
    </p-dialog>`
})
export class FenetreCampagneComponent {
  protected readonly fc = inject(FenetreCampagne);
  private readonly router = inject(Router);

  enregistree(c: Campagne): void {
    this.fc.fermer();
    this.fc.enregistree.set(c);
    this.router.navigate(['/campagnes', c.id]);
  }
}
