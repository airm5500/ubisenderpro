import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { EnvoiHistorique, Evolution, Page } from '../../core/modeles';
import { Courbe, Serie } from '../../core/courbe';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';

/** Tous les messages partis : relances, promotions, discussions et envois en masse, sur tous les canaux. */
@Component({
  selector: 'app-historique-envois',
  imports: [FormsModule, RouterLink, JourPipe, TelephonePipe, Courbe, TooltipModule, DialogModule],
  templateUrl: './historique-envois.html'
})
export class HistoriqueEnvois implements OnInit {
  private readonly api = inject(Api);
  protected readonly L = LIBELLES;
  protected readonly statuts = Object.entries(LIBELLES.statutMessage);
  protected readonly origines = Object.entries(LIBELLES.origineEnvoi).filter(([code]) => code !== 'REPONSE');

  criteres = { du: avant(30), au: '', origine: '', canal: '', statut: '', q: '' };
  protected readonly page = signal<Page<EnvoiHistorique> | null>(null);
  protected readonly ouvert = signal<string | null>(null);
  /** Message affiche en entier dans la fenetre de visualisation. */
  protected readonly visualise = signal<EnvoiHistorique | null>(null);
  protected readonly evolution = signal<Evolution | null>(null);
  protected readonly seriesEnvois: Serie[] = [
    { libelle: 'Envoyés', valeur: p => p.envoyes, teinte: 'bleu' },
    { libelle: 'Lus', valeur: p => p.lus, teinte: 'vert' },
    { libelle: 'En échec', valeur: p => p.echecs, teinte: 'gris' }
  ];
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
    this.api.getSilencieux<Evolution>('/api/historique-envois/evolution', this.filtres()).subscribe({
      next: ev => this.evolution.set(ev),
      error: () => this.evolution.set(null)
    });
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  basculer(e: EnvoiHistorique): void {
    this.ouvert.set(this.ouvert() === e.cle ? null : e.cle);
  }

  exporter(): void {
    this.api.telecharger('/api/historique-envois/export' + requete(this.filtres()), 'historique-envois.csv');
  }

  protected get numero(): number { return this.numeroPage; }

  private filtres(): Record<string, string | null> {
    return Object.fromEntries(Object.entries(this.criteres).map(([k, v]) => [k, v || null]));
  }

  private charger(): void {
    this.api.get<Page<EnvoiHistorique>>('/api/historique-envois', { ...this.filtres(), page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }
}

function avant(jours: number): string {
  const d = new Date();
  d.setDate(d.getDate() - jours);
  return d.toISOString().substring(0, 10);
}

function requete(filtres: Record<string, string | null>): string {
  const p = new URLSearchParams();
  Object.entries(filtres).forEach(([k, v]) => v && p.set(k, v));
  const texte = p.toString();
  return texte ? '?' + texte : '';
}
