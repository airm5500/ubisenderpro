import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Evolution, TableauBordMarketing } from '../../core/modeles';
import { Courbe, Serie } from '../../core/courbe';
import { JourPipe, LIBELLES, MontantPipe, aujourdhui } from '../../core/format';

/** Tableau de bord du marketing : campagnes, messages par canal, reponses et desabonnements de la periode. */
@Component({
  selector: 'app-tableau-bord-marketing',
  imports: [FormsModule, RouterLink, JourPipe, MontantPipe, Courbe],
  templateUrl: './tableau-bord-marketing.html'
})
export class TableauBordMarketingPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly L = LIBELLES;

  filtres = { du: aujourdhui(-29), au: aujourdhui() };
  protected readonly tb = signal<TableauBordMarketing | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly evolution = signal<Evolution | null>(null);
  protected readonly seriesMessages: Serie[] = [
    { libelle: 'Messages envoyés', valeur: p => p.envoyes, teinte: 'bleu' },
    { libelle: 'Messages lus', valeur: p => p.lus, teinte: 'vert' }
  ];
  protected readonly seriesRetours: Serie[] = [
    { libelle: 'Réponses reçues', valeur: p => p.reponses, teinte: 'vert', barres: true },
    { libelle: 'Désabonnements (STOP)', valeur: p => p.desabonnements, teinte: 'gris', barres: true }
  ];

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
    this.api.getSilencieux<Evolution>('/api/marketing/tableau-de-bord/evolution', this.filtres).subscribe({
      next: ev => this.evolution.set(ev),
      error: () => this.evolution.set(null)
    });
  }

  protected pc(v: number | null): string {
    return v === null ? '—' : `${String(v).replace('.', ',')} %`;
  }
}
