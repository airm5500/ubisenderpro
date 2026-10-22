import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { Api } from './api';
import { AnalyseCampagne } from './modeles';
import { Courbe, Serie } from './courbe';

/** Analyse d'une campagne lancee (remarque 5 du 22/10/2026) : chiffres, taux et courbe jour par jour, pour les deux modules. */
@Component({
  selector: 'app-analyse-campagne',
  imports: [Courbe],
  template: `
    @if (analyse(); as a) {
      <section class="bloc">
        <div class="titre"><h2 class="h2">Analyse de la campagne</h2></div>
        <div class="kvs">
          <div class="kv"><span>Cibles</span><strong>{{ a.cibles }}</strong></div>
          <div class="kv"><span>Envoyés</span><strong>{{ a.envoyes }}</strong></div>
          <div class="kv"><span>Distribués</span><strong>{{ a.distribues }}</strong><span class="second">{{ taux(a.tauxDistribution) }} des envoyés</span></div>
          <div class="kv"><span>Lus</span><strong>{{ a.lus }}</strong><span class="second">{{ taux(a.tauxLecture) }} des distribués</span></div>
          <div class="kv"><span>Échecs</span><strong>{{ a.echecs }}</strong><span class="second">{{ taux(a.tauxEchec) }} des tentatives</span></div>
          <div class="kv"><span>Réponses</span><strong>{{ a.reponses }}</strong><span class="second">{{ taux(a.tauxReponse) }} des envoyés</span></div>
          <div class="kv"><span>En attente</span><strong>{{ a.enAttente }}</strong></div>
          <div class="kv"><span>Écartés</span><strong>{{ a.exclus }}</strong></div>
        </div>
        @if (a.courbe.length) {
          <app-courbe [points]="a.courbe" [series]="series" libelle="Envoyés, distribués, lus et réponses par jour" />
        } @else {
          <p class="discret" style="margin: 0">La courbe apparaîtra avec les premiers envois.</p>
        }
        <p class="discret" style="margin: 0">« Distribué » et « lu » viennent des accusés de WhatsApp, de Meta ou du fournisseur SMS ; un e-mail n'en renvoie pas.</p>
      </section>
    }`
})
export class AnalyseCampagneBloc implements OnChanges {
  private readonly api = inject(Api);
  /** Adresse de la campagne dans son module, ex. /api/recouvrement/campagnes/12. */
  readonly url = input.required<string>();
  /** Change a chaque rechargement de la campagne, pour relire l'analyse. */
  readonly version = input<unknown>();
  protected readonly analyse = signal<AnalyseCampagne | null>(null);
  protected readonly series: Serie[] = [
    { libelle: 'Envoyés', valeur: p => p.envoyes, teinte: 'bleu' },
    { libelle: 'Distribués', valeur: p => p.distribues, teinte: 'bleu-clair' },
    { libelle: 'Lus', valeur: p => p.lus, teinte: 'vert' },
    { libelle: 'Réponses', valeur: p => p.reponses, teinte: 'gris' }
  ];

  ngOnChanges(): void {
    this.api.getSilencieux<AnalyseCampagne>(this.url() + '/analyse').subscribe(a => this.analyse.set(a));
  }

  protected taux(t: number | null): string {
    return t === null ? '—' : `${t.toLocaleString('fr-FR')} %`;
  }
}
