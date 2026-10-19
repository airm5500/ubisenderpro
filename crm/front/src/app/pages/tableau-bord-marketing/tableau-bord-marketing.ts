import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { TableauBordMarketing } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, aujourdhui } from '../../core/format';

/** Tableau de bord du marketing : campagnes, messages par canal, reponses et desabonnements de la periode. */
@Component({
  selector: 'app-tableau-bord-marketing',
  imports: [FormsModule, RouterLink, JourPipe, MontantPipe],
  templateUrl: './tableau-bord-marketing.html'
})
export class TableauBordMarketingPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly L = LIBELLES;

  filtres = { du: aujourdhui(-29), au: aujourdhui() };
  protected readonly tb = signal<TableauBordMarketing | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly entonnoir = computed(() => {
    const t = this.tb();
    if (!t) return [];
    const base = Math.max(t.messagesTentes, 1);
    return [
      { libelle: 'Messages tentés', valeur: t.messagesTentes, classe: '' },
      { libelle: 'Envoyés', valeur: t.messagesEnvoyes, classe: '' },
      { libelle: 'Distribués', valeur: t.messagesDistribues, classe: '' },
      { libelle: 'Lus', valeur: t.messagesLus, classe: 'vert' },
      { libelle: 'En échec', valeur: t.messagesEchecs, classe: 'alerte' }
    ].map(e => ({ ...e, largeur: Math.round(e.valeur * 100 / base) }));
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.erreur.set(null);
    this.api.getSilencieux<TableauBordMarketing>('/api/marketing/tableau-de-bord', this.filtres).subscribe({
      next: tb => this.tb.set(tb),
      error: e => this.erreur.set(e?.error?.message ?? 'Tableau de bord indisponible.')
    });
  }

  protected pc(v: number | null): string {
    return v === null ? '—' : `${String(v).replace('.', ',')} %`;
  }
}
