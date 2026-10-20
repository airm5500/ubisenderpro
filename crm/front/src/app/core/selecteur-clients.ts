import { Component, inject, input, model, output, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { Api } from './api';
import { Client, Page } from './modeles';
import { Referentiels } from './referentiels';
import { TelephonePipe } from './format';

/** Client retenu par le selecteur : de quoi l'afficher dans une liste de destinataires. */
export interface ClientChoisi { id: number; code: string; raisonSociale: string; agenceLibelle: string; }

/**
 * Choix de clients dans une fenetre (repris d'UbiSenderPro) : recherche, filtres agence et segment, cases a cocher et
 * « Tout cocher » sur la page affichee. Le choix n'est rendu qu'a la validation.
 */
@Component({
  selector: 'app-selecteur-clients',
  imports: [FormsModule, DialogModule, AsyncPipe, TelephonePipe],
  template: `
    <p-dialog header="Choisir des clients" [visible]="ouvert()" (visibleChange)="!$event && ouvert.set(false)" (onShow)="preparer()"
              [modal]="true" [style]="{ width: '860px', maxWidth: '96vw' }">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; align-items: end">
        <label class="f">Recherche<input class="sel" name="q" [(ngModel)]="q" (ngModelChange)="chercher()" placeholder="Code, pharmacie ou pharmacien"></label>
        <label class="f">Agence
          <select class="sel" name="agenceId" [(ngModel)]="agenceId" (ngModelChange)="chercher()">
            <option [ngValue]="null">Toutes</option>
            @for (a of referentiels.agences | async; track a.id) { <option [ngValue]="a.id">{{ a.libelle }}</option> }
          </select>
        </label>
        <label class="f">Segment
          <select class="sel" name="segment" [(ngModel)]="segment" (ngModelChange)="chercher()">
            <option [ngValue]="null">Tous</option>
            @for (s of referentiels.segments | async; track s.code) { <option [ngValue]="s.code">{{ s.libelle }}</option> }
          </select>
        </label>
      </div>
      <div class="defile" style="max-height: 50vh; margin-top: 12px">
        <table class="t">
          <thead><tr>
            <th style="width: 36px"><input type="checkbox" aria-label="Tout cocher sur la page" [checked]="toutCoche()" (change)="toutCocher($any($event.target).checked)"></th>
            <th>Code</th><th>Pharmacie</th><th>Agence</th><th>Segment</th><th>Numéro principal</th></tr></thead>
          <tbody>
            @for (c of page()?.elements; track c.id) {
              <tr class="cliquable" (click)="basculer(c)" [class.choisi]="choisis.has(c.id)">
                <td><input type="checkbox" [checked]="choisis.has(c.id)" (click)="$event.stopPropagation()" (change)="basculer(c)" [attr.aria-label]="c.raisonSociale"></td>
                <td class="nw">{{ c.code }}</td><td>{{ c.raisonSociale }}</td><td>{{ c.agenceLibelle }}</td><td>{{ c.segmentationLibelle }}</td>
                <td class="nw">{{ c.numeroPrincipal ? (c.numeroPrincipal | telephone) : '—' }}</td>
              </tr>
            } @empty { <tr><td colspan="6" class="vide">{{ page() ? 'Aucun client ne correspond.' : 'Chargement…' }}</td></tr> }
          </tbody>
        </table>
      </div>
      <div class="actions" style="justify-content: space-between; margin-top: 12px; flex-wrap: wrap">
        <span class="discret">{{ choisis.size }} client(s) coché(s) · {{ page()?.total ?? 0 }} trouvé(s)</span>
        <span class="actions">
          @if ((page()?.page ?? 0) > 0) { <button class="bs petit" type="button" (click)="aller(-1)">Précédent</button> }
          @if (page() && (page()!.page + 1) * page()!.taille < page()!.total) { <button class="bs petit" type="button" (click)="aller(1)">Suivant</button> }
          <button class="bs" type="button" (click)="ouvert.set(false)">Annuler</button>
          <button class="bp" type="button" (click)="valider()" [disabled]="!choisis.size">Ajouter {{ choisis.size }} client(s)</button>
        </span>
      </div>
    </p-dialog>`
})
export class SelecteurClients {
  private readonly api = inject(Api);
  protected readonly referentiels = inject(Referentiels);

  readonly ouvert = model(false);
  /** Clients deja retenus : coches a l'ouverture. */
  readonly deja = input<ClientChoisi[]>([]);
  readonly choisir = output<ClientChoisi[]>();

  protected readonly page = signal<Page<Client> | null>(null);
  protected choisis = new Map<number, ClientChoisi>();
  protected q = '';
  protected agenceId: number | null = null;
  protected segment: string | null = null;
  private numeroPage = 0;
  private minuterie?: ReturnType<typeof setTimeout>;

  preparer(): void {
    this.choisis = new Map(this.deja().map(c => [c.id, c]));
    this.numeroPage = 0;
    this.charger();
  }

  chercher(): void {
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(() => { this.numeroPage = 0; this.charger(); }, 250);
  }

  aller(sens: number): void {
    this.numeroPage += sens;
    this.charger();
  }

  basculer(c: Client): void {
    if (this.choisis.has(c.id)) {
      this.choisis.delete(c.id);
    } else {
      this.choisis.set(c.id, { id: c.id, code: c.code, raisonSociale: c.raisonSociale, agenceLibelle: c.agenceLibelle });
    }
  }

  toutCoche(): boolean {
    const elements = this.page()?.elements ?? [];
    return elements.length > 0 && elements.every(c => this.choisis.has(c.id));
  }

  toutCocher(oui: boolean): void {
    for (const c of this.page()?.elements ?? []) {
      if (oui && !this.choisis.has(c.id)) this.basculer(c);
      if (!oui && this.choisis.has(c.id)) this.choisis.delete(c.id);
    }
  }

  valider(): void {
    this.choisir.emit([...this.choisis.values()]);
    this.ouvert.set(false);
  }

  private charger(): void {
    this.api.get<Page<Client>>('/api/clients', { q: this.q || null, agenceId: this.agenceId, segmentation: this.segment, page: this.numeroPage, taille: 50 })
      .subscribe(p => this.page.set(p));
  }
}
