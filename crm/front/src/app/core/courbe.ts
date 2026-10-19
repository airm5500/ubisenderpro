import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { CharteService } from './charte';
import { PointEvolution } from './modeles';

/** Teintes autorisees : les deux couleurs de la charte, leurs versions claires et le gris du texte secondaire. */
export type Teinte = 'bleu' | 'bleu-clair' | 'vert' | 'vert-clair' | 'gris';

export interface Serie {
  libelle: string;
  valeur: (p: PointEvolution) => number | null;
  teinte: Teinte;
  /** Barres plutot que ligne. */
  barres?: boolean;
}

const nombre = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/** 1 500 000 avec des espaces de milliers (l'espace fine insecable de fr-FR devient une espace simple sur le canevas). */
export function milliers(v: number): string {
  return nombre.format(v).replace(/[  ]/g, ' ');
}

/** jj/mm (axe) ou jj/mm/aaaa (infobulle) d'une date ISO. */
function jjmm(iso: string, annee = false): string {
  const [a, m, j] = iso.substring(0, 10).split('-');
  return annee ? `${j}/${m}/${a}` : `${j}/${m}`;
}

/** Couleur de la charte lue dans les variables CSS (parametrables), avec sa valeur livree en secours. */
function variable(nom: string, defaut: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(nom).trim();
  return v || defaut;
}

/** Melange une couleur #rrggbb avec du blanc (part = proportion de blanc). */
function eclaircir(couleur: string, part: number): string {
  const hex = couleur.replace('#', '');
  const plein = hex.length === 3 ? hex.split('').map(c => c + c).join('') : hex;
  if (!/^[0-9a-fA-F]{6}$/.test(plein)) return couleur;
  const canal = (i: number) => Math.round(parseInt(plein.substring(i, i + 2), 16) * (1 - part) + 255 * part);
  return `rgb(${canal(0)}, ${canal(2)}, ${canal(4)})`;
}

/** Courbes d'evolution d'un tableau de bord (PrimeNG Chart, chart.js) aux couleurs de la charte. */
@Component({
  selector: 'app-courbe',
  imports: [ChartModule],
  template: `<p-chart [type]="type()" [data]="donnees()" [options]="options()" width="100%" [height]="hauteur()" [ariaLabel]="libelle()" />`
})
export class Courbe {
  private readonly charte = inject(CharteService);
  readonly points = input.required<PointEvolution[]>();
  readonly series = input.required<Serie[]>();
  /** JOUR ou SEMAINE : titre des infobulles. */
  readonly pas = input<string>('JOUR');
  /** Valeurs en FCFA. */
  readonly montant = input(false);
  readonly hauteur = input('260px');
  readonly libelle = input('Courbe d\'évolution');

  protected readonly type = computed(() => this.series().every(s => s.barres) ? 'bar' : 'line');

  protected readonly donnees = computed(() => {
    this.charte.charte(); // couleurs relues quand la charte change
    const couleurs: Record<Teinte, string> = {
      bleu: variable('--bleu', '#05253D'),
      'bleu-clair': eclaircir(variable('--bleu', '#05253D'), 0.55),
      vert: variable('--vert', '#51D78E'),
      'vert-clair': eclaircir(variable('--vert', '#51D78E'), 0.5),
      gris: variable('--texte-2', '#51606C')
    };
    const points = this.points();
    return {
      labels: points.map(p => jjmm(p.date)),
      datasets: this.series().map(s => ({
        type: s.barres ? 'bar' : 'line',
        label: s.libelle,
        data: points.map(p => s.valeur(p)),
        borderColor: couleurs[s.teinte],
        backgroundColor: couleurs[s.teinte],
        borderWidth: s.barres ? 0 : 2,
        borderRadius: s.barres ? 3 : 0,
        pointRadius: points.length > 31 ? 0 : 2,
        pointHoverRadius: 4,
        tension: 0.25,
        fill: false,
        maxBarThickness: 18
      }))
    };
  });

  protected readonly options = computed(() => {
    this.charte.charte();
    const gris = variable('--texte-2', '#51606C');
    const filet = eclaircir(gris.startsWith('#') ? gris : '#51606C', 0.85);
    const police = { family: getComputedStyle(document.body).fontFamily || 'Manrope, sans-serif', size: 12 };
    const montant = this.montant();
    const points = this.points();
    const semaine = this.pas() === 'SEMAINE';
    const texte = (v: number) => milliers(v) + (montant ? ' FCFA' : '');
    return {
      maintainAspectRatio: false,
      responsive: true,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'bottom', labels: { color: gris, font: police, boxWidth: 12, boxHeight: 12, usePointStyle: true } },
        tooltip: {
          titleFont: police, bodyFont: police,
          callbacks: {
            title: (items: { dataIndex: number }[]) => {
              const p = points[items[0]?.dataIndex];
              return p ? (semaine ? 'Semaine du ' : '') + jjmm(p.date, true) : '';
            },
            label: (item: { dataset: { label?: string }; raw: unknown }) =>
              item.raw === null || item.raw === undefined ? `${item.dataset.label} : —` : `${item.dataset.label} : ${texte(Number(item.raw))}`
          }
        }
      },
      scales: {
        x: { ticks: { color: gris, font: police, maxRotation: 0, autoSkip: true, autoSkipPadding: 12 }, grid: { display: false } },
        y: {
          beginAtZero: true,
          ticks: { color: gris, font: police, precision: 0, callback: (v: number | string) => milliers(Number(v)) },
          grid: { color: filet },
          title: montant ? { display: true, text: 'FCFA', color: gris, font: police } : { display: false }
        }
      }
    };
  });
}
