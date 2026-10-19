import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agent, Evolution, TableauBord } from '../../core/modeles';
import { Courbe, Serie } from '../../core/courbe';
import { JourPipe, MontantPipe, aujourdhui } from '../../core/format';

/** Tableau de bord du recouvrement : l'agent voit son portefeuille, la direction d'agence ses agences, la direction tout. */
@Component({
  selector: 'app-tableau-bord',
  imports: [FormsModule, NgTemplateOutlet, RouterLink, JourPipe, MontantPipe, Courbe],
  templateUrl: './tableau-bord.html'
})
export class TableauBordPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  private readonly ref = inject(Referentiels);

  filtres = { du: aujourdhui(-29), au: aujourdhui(), agenceId: null as number | null, agentId: null as number | null };
  protected readonly tb = signal<TableauBord | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly evolution = signal<Evolution | null>(null);
  protected readonly seriesRelances: Serie[] = [
    { libelle: 'Envoyées', valeur: p => p.envoyes, teinte: 'bleu' },
    { libelle: 'Distribuées', valeur: p => p.distribues, teinte: 'bleu-clair' },
    { libelle: 'Lues', valeur: p => p.lus, teinte: 'vert' }
  ];
  protected readonly seriesEncaisse: Serie[] = [{ libelle: 'Montant encaissé', valeur: p => p.montantEncaisse, teinte: 'vert', barres: true }];
  protected readonly seriesEchu: Serie[] = [{ libelle: 'Solde dû échu', valeur: p => p.soldeEchu, teinte: 'bleu' }];
  protected readonly agents = signal<Agent[]>([]);
  private readonly toutesAgences = toSignal(this.ref.agences, { initialValue: [] });
  protected readonly agences = computed(() => {
    const u = this.auth.utilisateur();
    return u?.visionGlobale ? this.toutesAgences() : this.toutesAgences().filter(a => u?.agenceIds?.includes(a.id));
  });

  /** Base de l'entonnoir : messages tentes (100 %). */
  protected readonly entonnoir = computed(() => {
    const t = this.tb()?.total;
    if (!t) return [];
    const base = Math.max(t.messagesTentes, 1);
    return [
      { libelle: 'Relances tentées', valeur: t.messagesTentes, classe: '' },
      { libelle: 'Envoyées', valeur: t.messagesEnvoyes, classe: '' },
      { libelle: 'Distribuées', valeur: t.messagesDistribues, classe: '' },
      { libelle: 'Lues', valeur: t.messagesLus, classe: '' },
      { libelle: 'En échec', valeur: t.messagesEchecs, classe: 'alerte' }
    ].map(e => ({ ...e, largeur: Math.round(e.valeur * 100 / base) }));
  });
  protected readonly maxTranche = computed(() => Math.max(1, ...(this.tb()?.balanceAgee ?? []).map(b => b.montant ?? 0)));

  ngOnInit(): void {
    if (!this.auth.portefeuillePersonnel()) this.chargerAgents();
    this.charger();
  }

  agenceChoisie(): void {
    this.filtres.agentId = null;
    this.chargerAgents();
    this.charger();
  }

  charger(): void {
    this.erreur.set(null);
    this.api.getSilencieux<TableauBord>('/api/recouvrement/tableau-de-bord', this.filtres).subscribe({
      next: tb => this.tb.set(tb),
      error: e => this.erreur.set(e?.error?.message ?? 'Tableau de bord indisponible.')
    });
    this.api.getSilencieux<Evolution>('/api/recouvrement/tableau-de-bord/evolution', this.filtres).subscribe({
      next: ev => this.evolution.set(ev),
      error: () => this.evolution.set(null)
    });
  }

  protected pc(v: number | null): string {
    return v === null ? '—' : `${String(v).replace('.', ',')} %`;
  }

  private chargerAgents(): void {
    this.ref.agents(this.filtres.agenceId).subscribe(a => this.agents.set(a));
  }
}
