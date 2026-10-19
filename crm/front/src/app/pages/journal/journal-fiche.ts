import { Component, OnChanges, ViewEncapsulation, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Page } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { JournalLigne, auteur } from './journal-modeles';

/** Bloc « Modifications de la fiche » : les dernieres actions du journal sur un client et ses factures. */
@Component({
  // Pose sur la section du bloc pour garder l'habillage des panneaux de la fiche (themes A, B, C)
  selector: 'section[appJournalFiche]',
  imports: [RouterLink, JourPipe],
  styleUrl: './journal.css',
  encapsulation: ViewEncapsulation.None,
  template: `
      <div class="titre"><h2 class="h2">Modifications de la fiche</h2>
        <a routerLink="/journal" [queryParams]="{ clientId: clientId(), client: libelle() }">Tout le journal</a></div>
      @for (l of page()?.elements; track l.cle) {
        <div class="journal-ev">
          <div><span [class]="'journal-nature n-' + l.nature">{{ l.natureLibelle }}</span></div>
          <div>
            <div class="discret">{{ l.le | jour: true }} · {{ auteur(l) }}</div>
            <div class="journal-ev-titre">{{ l.typeLibelle }} · {{ l.actionLibelle }}</div>
            @if (l.details) { <div class="journal-ev-detail">{{ l.details }}</div> }
          </div>
        </div>
      } @empty {
        <p class="discret">{{ page() ? 'Aucune modification enregistrée pour ce client.' : 'Chargement…' }}</p>
      }
      @if ((page()?.total ?? 0) > taille) {
        <p class="discret">{{ page()!.total - taille }} action(s) plus ancienne(s) dans le journal.</p>
      }
  `
})
export class JournalFiche implements OnChanges {
  private readonly api = inject(Api);
  readonly clientId = input.required<number>();
  readonly libelle = input('');
  protected readonly auteur = auteur;
  protected readonly page = signal<Page<JournalLigne> | null>(null);
  protected readonly taille = 8;

  ngOnChanges(): void {
    this.api.get<Page<JournalLigne>>('/api/journal', { clientId: this.clientId(), taille: this.taille })
      .subscribe(p => this.page.set(p));
  }
}
