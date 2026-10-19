import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Referentiels } from '../../core/referentiels';
import { Referentiel, ResultatMiseAJour, Segment } from '../../core/modeles';

const CHAMPS: { code: string; libelle: string }[] = [
  { code: 'SEGMENT', libelle: 'Segment' },
  { code: 'AGENCE', libelle: 'Agence' },
  { code: 'SOUS_AGENCE', libelle: 'Sous-agence' },
  { code: 'LOCALISATION', libelle: 'Région, ville et commune' },
  { code: 'TOURNEE', libelle: 'Tournée' }
];

/** Change les champs coches sur tous les clients selectionnes, sans toucher aux autres. */
@Component({
  selector: 'app-mise-a-jour-selective',
  imports: [FormsModule, AsyncPipe, DialogModule],
  templateUrl: './mise-a-jour-selective.html'
})
export class MiseAJourSelective {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly ref = inject(Referentiels);
  protected readonly CHAMPS = CHAMPS;

  readonly ouvert = model(false);
  readonly clientIds = input<number[]>([]);
  /** Agences des clients selectionnes : la tournee proposee est celle de l'agence commune. */
  readonly agenceIds = input<number[]>([]);
  readonly enregistre = output<ResultatMiseAJour>();

  protected readonly segments = this.api.get<Segment[]>('/api/clients/segments');
  protected readonly listes = signal<Record<string, Referentiel[]>>({ REGION: [], VILLE: [], COMMUNE: [], TOURNEE: [] });
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  champs = new Set<string>();
  f = vide();

  preparer(): void {
    this.champs = new Set();
    this.f = vide();
    this.erreur.set(null);
    this.api.get<Referentiel[]>('/api/referentiels').subscribe(toutes => {
      const listes: Record<string, Referentiel[]> = { REGION: [], VILLE: [], COMMUNE: [], TOURNEE: [] };
      toutes.forEach(r => listes[r.type]?.push(r));
      this.listes.set(listes);
    });
  }

  basculer(code: string, coche: boolean): void {
    coche ? this.champs.add(code) : this.champs.delete(code);
  }

  agenceCible(): number | null {
    if (this.champs.has('AGENCE')) return this.f.agenceId;
    const ids = [...new Set(this.agenceIds())];
    return ids.length === 1 ? ids[0] : null;
  }

  tournees(): Referentiel[] {
    const a = this.agenceCible();
    return this.listes()['TOURNEE'].filter(t => a === null || t.agenceId === a);
  }

  villes(): Referentiel[] {
    return this.listes()['VILLE'].filter(v => !this.f.regionId || !v.parentId || v.parentId === this.f.regionId);
  }

  communes(): Referentiel[] {
    return this.listes()['COMMUNE'].filter(c => !this.f.villeId || c.parentId === this.f.villeId);
  }

  communeChoisie(): void {
    const c = this.listes()['COMMUNE'].find(x => x.id === this.f.communeId);
    if (c) { this.f.villeId = c.parentId; this.villeChoisie(); }
  }

  villeChoisie(): void {
    const v = this.listes()['VILLE'].find(x => x.id === this.f.villeId);
    if (v?.parentId) this.f.regionId = v.parentId;
  }

  enregistrer(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.postSilencieux<ResultatMiseAJour>('/api/clients/mise-a-jour-selective', {
      ...this.f, segmentation: this.f.segmentation || null, clientIds: this.clientIds(), champs: [...this.champs]
    }).subscribe({
      next: r => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Clients mis à jour',
          detail: `${r.nbModifies} modifié(s)${r.nbInchanges ? ', ' + r.nbInchanges + ' déjà à jour' : ''}` });
        this.enregistre.emit(r);
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Mise à jour impossible.'); }
    });
  }
}

function vide() {
  return { segmentation: '', agenceId: null as number | null, sousAgence: '', regionId: null as number | null,
    villeId: null as number | null, communeId: null as number | null, tourneeId: null as number | null };
}
