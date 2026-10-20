import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { JourPipe, MontantPipe } from './format';

export interface ActiviteCalendrier {
  date: string; type: string; libelle: string; nombre: number | null; montant: number | null; lien: string | null;
}

interface Jour { date: string; numero: number; duMois: boolean; aujourdhui: boolean; activites: ActiviteCalendrier[]; }

const MOIS = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

/** Icone et teinte (couleurs de la charte et leurs nuances) de chaque type d'activite. */
const TYPES: Record<string, { icone: string; teinte: 'bleu' | 'vert' | 'clair' | 'gris' }> = {
  RELANCES: { icone: 'pi-send', teinte: 'bleu' },
  MESSAGES: { icone: 'pi-send', teinte: 'bleu' },
  ECHECS: { icone: 'pi-times-circle', teinte: 'gris' },
  CAMPAGNE: { icone: 'pi-megaphone', teinte: 'bleu' },
  CAMPAGNE_PROGRAMMEE: { icone: 'pi-clock', teinte: 'clair' },
  ENCAISSEMENT: { icone: 'pi-wallet', teinte: 'vert' },
  PROMESSE: { icone: 'pi-check-square', teinte: 'vert' },
  PROMESSE_ECHEANCE: { icone: 'pi-calendar-clock', teinte: 'clair' },
  ECHEANCE: { icone: 'pi-calendar', teinte: 'clair' },
  REPONSES: { icone: 'pi-comments', teinte: 'vert' },
  DESABONNEMENTS: { icone: 'pi-ban', teinte: 'gris' },
  PROMOTION_DEBUT: { icone: 'pi-tag', teinte: 'vert' },
  PROMOTION_FIN: { icone: 'pi-tag', teinte: 'gris' },
  EVENEMENT: { icone: 'pi-star', teinte: 'clair' }
};

/**
 * Calendrier mensuel de l'activite d'un tableau de bord, repris d'UbiSenderPro : une case par jour (lundi a dimanche),
 * les activites du jour en pastilles, le detail du jour choisi en dessous.
 */
@Component({
  selector: 'app-calendrier-activite',
  imports: [RouterLink, JourPipe, MontantPipe],
  template: `
    <div class="barre">
      <button type="button" class="bi" (click)="decaler(-1)" aria-label="Mois précédent"><i class="pi pi-chevron-left"></i></button>
      <strong class="mois">{{ libelleMois() }}</strong>
      <button type="button" class="bi" (click)="decaler(1)" aria-label="Mois suivant"><i class="pi pi-chevron-right"></i></button>
      <button type="button" class="bs petit" (click)="revenir()">Aujourd'hui</button>
      <span class="discret">{{ chargement() ? 'Chargement…' : erreur() ?? '' }}</span>
    </div>
    <div class="grille" role="grid" [attr.aria-label]="'Activité de ' + libelleMois()">
      @for (j of ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']; track j) { <div class="js" role="columnheader">{{ j }}</div> }
      @for (j of jours(); track j.date) {
        <button type="button" class="cj" role="gridcell" [class.hors]="!j.duMois" [class.auj]="j.aujourdhui" [class.choisi]="choisi() === j.date"
                [class.plein]="j.activites.length > 0" (click)="choisi.set(j.date)" [attr.aria-label]="(j.date | jour) + ' : ' + j.activites.length + ' activité(s)'">
          <span class="nj">{{ j.numero }}</span>
          @for (a of j.activites.slice(0, 3); track $index) {
            <span class="pastille" [class]="'pastille t-' + teinte(a.type)" [title]="a.libelle"><i class="pi" [class]="'pi ' + icone(a.type)"></i>{{ court(a) }}</span>
          }
          @if (j.activites.length > 3) { <span class="plus">+ {{ j.activites.length - 3 }}</span> }
        </button>
      }
    </div>
    @if (detail(); as d) {
      <section class="detail">
        <div class="titre-detail">
          <strong>{{ d.date | jour }}</strong>
          <button type="button" class="bs petit" (click)="jourChoisi.emit(d.date)">Voir ce jour dans le tableau de bord</button>
        </div>
        @for (a of d.activites; track $index) {
          <div class="ligne">
            <span class="ic" [class]="'ic t-' + teinte(a.type)"><i class="pi" [class]="'pi ' + icone(a.type)"></i></span>
            <span class="lib">{{ a.libelle }}@if (a.montant !== null) { <strong> · {{ a.montant | montant }} FCFA</strong> }</span>
            @if (a.lien) { <a class="bi" [routerLink]="a.lien" aria-label="Ouvrir"><i class="pi pi-arrow-right"></i></a> }
          </div>
        } @empty {
          <p class="discret">Aucune activité ce jour-là.</p>
        }
      </section>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 12px; }
    .barre { display: flex; align-items: center; gap: 8px; }
    .mois { min-width: 150px; text-align: center; color: var(--bleu); font-size: 16px; }
    .petit { height: 32px; padding: 0 12px; font-size: 13px; }
    .grille { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; }
    .js { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .08em; color: var(--texte-2); text-align: center; padding: 4px 0; }
    .cj { min-height: 92px; display: flex; flex-direction: column; align-items: stretch; gap: 3px; padding: 4px; border: 1px solid var(--filet);
      border-radius: 8px; background: #fff; font: inherit; text-align: left; cursor: pointer; min-width: 0; }
    .cj:hover { background: var(--survol); }
    .cj.hors { opacity: .45; }
    .cj.auj { border-color: var(--bleu); box-shadow: inset 0 0 0 1px var(--bleu); }
    .cj.choisi { background: color-mix(in srgb, var(--vert) 14%, #fff); border-color: var(--vert); }
    .nj { font-size: 12px; font-weight: 700; color: var(--bleu); }
    .pastille { display: flex; align-items: center; gap: 4px; font-size: 11px; line-height: 16px; padding: 1px 4px; border-radius: 4px;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis; border-left: 3px solid; }
    .pastille .pi { font-size: 10px; }
    .plus { font-size: 11px; color: var(--texte-2); font-weight: 700; }
    .t-bleu { background: color-mix(in srgb, var(--bleu) 8%, #fff); border-color: var(--bleu); color: var(--bleu); }
    .t-vert { background: color-mix(in srgb, var(--vert) 16%, #fff); border-color: var(--vert); color: #157A47; }
    .t-clair { background: color-mix(in srgb, var(--bleu) 4%, #fff); border-color: color-mix(in srgb, var(--bleu) 40%, #fff); color: var(--bleu); }
    .t-gris { background: #F2F4F6; border-color: #9DB0BF; color: var(--texte-2); }
    .detail { border-top: 1px solid var(--filet); padding-top: 10px; display: flex; flex-direction: column; gap: 6px; }
    .titre-detail { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--bleu); }
    .ligne { display: flex; align-items: center; gap: 10px; font-size: 14px; }
    .ic { width: 28px; height: 28px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; flex: none; border-left: 0; }
    .lib { flex: 1; }
  `
})
export class CalendrierActivite {
  private readonly api = inject(Api);

  /** Adresse du calendrier du tableau de bord ; recoit du et au, plus les filtres. */
  readonly url = input.required<string>();
  readonly filtres = input<Record<string, string | number | null>>({});
  /** Jour a afficher seul dans le tableau de bord. */
  readonly jourChoisi = output<string>();

  protected readonly mois = signal(premierDuMois(new Date()));
  protected readonly choisi = signal<string | null>(null);
  protected readonly chargement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  private readonly activites = signal<ActiviteCalendrier[]>([]);

  protected readonly libelleMois = computed(() => `${MOIS[this.mois().getUTCMonth()]} ${this.mois().getUTCFullYear()}`);

  /** Semaines completes (lundi a dimanche) qui couvrent le mois. */
  protected readonly jours = computed<Jour[]>(() => {
    const debut = this.mois();
    const auj = iso(new Date());
    const parJour = new Map<string, ActiviteCalendrier[]>();
    this.activites().forEach(a => parJour.set(a.date, [...(parJour.get(a.date) ?? []), a]));
    const [du, au] = bornes(debut);
    const jours: Jour[] = [];
    for (const d = new Date(du); d <= au; d.setUTCDate(d.getUTCDate() + 1)) {
      const cle = iso(d);
      jours.push({ date: cle, numero: d.getUTCDate(), duMois: d.getUTCMonth() === debut.getUTCMonth(), aujourdhui: cle === auj, activites: parJour.get(cle) ?? [] });
    }
    return jours;
  });

  protected readonly detail = computed(() => this.jours().find(j => j.date === this.choisi()) ?? null);

  constructor() {
    effect(() => this.charger(this.mois(), this.filtres()));
  }

  decaler(n: number): void {
    const m = this.mois();
    this.mois.set(new Date(Date.UTC(m.getUTCFullYear(), m.getUTCMonth() + n, 1)));
    this.choisi.set(null);
  }

  revenir(): void {
    this.mois.set(premierDuMois(new Date()));
    this.choisi.set(iso(new Date()));
  }

  protected icone(type: string): string { return TYPES[type]?.icone ?? 'pi-circle'; }
  protected teinte(type: string): string { return TYPES[type]?.teinte ?? 'bleu'; }

  /** Texte court d'une pastille. */
  protected court(a: ActiviteCalendrier): string { return texteCourt(a); }

  private charger(mois: Date, filtres: Record<string, string | number | null>): void {
    const [du, au] = bornes(mois);
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.getSilencieux<ActiviteCalendrier[]>(this.url(), { ...filtres, du: iso(du), au: iso(au) }).subscribe({
      next: a => { this.activites.set(a); this.chargement.set(false); },
      error: e => { this.activites.set([]); this.chargement.set(false); this.erreur.set(e?.error?.message ?? 'Calendrier indisponible.'); }
    });
  }
}

export function premierDuMois(d: Date): Date {
  return new Date(Date.UTC(d.getFullYear(), d.getMonth(), 1));
}

/** Du lundi de la premiere semaine au dimanche de la derniere. */
export function bornes(mois: Date): [Date, Date] {
  const du = new Date(mois);
  du.setUTCDate(1 - (du.getUTCDay() + 6) % 7);
  const au = new Date(Date.UTC(mois.getUTCFullYear(), mois.getUTCMonth() + 1, 0));
  au.setUTCDate(au.getUTCDate() + (7 - au.getUTCDay()) % 7);
  return [du, au];
}

export function iso(d: Date): string {
  return d.toISOString().substring(0, 10);
}

/** Texte court d'une pastille : le montant ou le nombre pour un cumul, le nom pour une campagne, une promotion ou un evenement. */
export function texteCourt(a: ActiviteCalendrier): string {
  if (a.type === 'ENCAISSEMENT' && a.montant !== null) return new Intl.NumberFormat('fr-FR', { notation: 'compact' }).format(a.montant);
  if (a.nombre !== null) return String(a.nombre);
  return a.libelle.replace(/^(Lancement|Programmée) : |^(Début|Fin) de la promotion /, '');
}
