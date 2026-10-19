import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Referentiel, Segment } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { Referentiels as ListesReference } from '../../core/referentiels';
import { BADGES, BadgeSegment, CODES_BADGES } from '../../core/badges';

export type TypeReferentiel = 'REGION' | 'VILLE' | 'COMMUNE' | 'TOURNEE' | 'SEGMENT';

const NOMS: Record<TypeReferentiel, [string, string, string]> = {
  REGION: ['région', 'Nouvelle région', 'La ville choisie sur une fiche client donne sa région.'],
  VILLE: ['ville', 'Nouvelle ville', "Rattachez chaque ville à sa région : la fiche client et l'import la complètent d'office."],
  COMMUNE: ['commune', 'Nouvelle commune', 'Chaque commune appartient à une ville ; la commune choisie donne la ville et la région du client.'],
  TOURNEE: ['tournée', 'Nouvelle tournée', "Une tournée de livraison est desservie par une agence ; un client ne prend qu'une tournée de son agence."],
  SEGMENT: ['segment', 'Nouveau segment', "Le code est celui des fichiers importés (P, S, B...). Le badge s'affiche sur la fiche de chaque client du segment."]
};

interface Edition { code: string; libelle: string; parentId: number | null; agenceId: number | null; badge: string; }

/** Listes de reference des fiches clients : regions, villes, communes, tournees et segments avec leur badge. Rien ne se supprime. */
@Component({
  selector: 'app-referentiels',
  imports: [TooltipModule, FormsModule, DialogModule, JourPipe, BadgeSegment, AsyncPipe],
  templateUrl: './referentiels.html'
})
export class ReferentielsListe implements OnChanges {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly listes = inject(ListesReference);

  readonly type = input.required<TypeReferentiel>();

  protected readonly entrees = signal<(Referentiel | Segment)[] | null>(null);
  protected readonly parents = signal<Referentiel[]>([]);
  protected readonly archives = signal(false);
  protected readonly filtre = signal('');
  protected readonly ouvert = signal(false);
  protected readonly cible = signal<Referentiel | Segment | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly badges = CODES_BADGES;
  protected readonly B = BADGES;
  edition: Edition = { code: '', libelle: '', parentId: null, agenceId: null, badge: 'ETOILE' };

  protected readonly noms = computed(() => NOMS[this.type()]);
  protected readonly visibles = computed(() => {
    const f = this.filtre().trim().toLowerCase();
    return (this.entrees() ?? []).filter(e => !f || e.libelle.toLowerCase().includes(f)
      || ('parentLibelle' in e && (e.parentLibelle ?? e.agenceLibelle ?? '').toLowerCase().includes(f)));
  });

  ngOnChanges(): void {
    this.archives.set(false);
    this.filtre.set('');
    this.charger();
    const parent = this.type() === 'VILLE' ? 'REGION' : this.type() === 'COMMUNE' ? 'VILLE' : null;
    if (parent) {
      this.api.get<Referentiel[]>('/api/referentiels', { type: parent }).subscribe(p => this.parents.set(p));
    }
  }

  segment(e: Referentiel | Segment): Segment {
    return e as Segment;
  }

  ref(e: Referentiel | Segment): Referentiel {
    return e as Referentiel;
  }

  basculerArchives(): void {
    this.archives.set(!this.archives());
    this.charger();
  }

  nouvelle(): void {
    this.cible.set(null);
    this.edition = { code: '', libelle: '', parentId: null, agenceId: null, badge: 'ETOILE' };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  modifier(e: Referentiel | Segment): void {
    this.cible.set(e);
    const r = e as Referentiel;
    this.edition = { code: e.code ?? '', libelle: e.libelle, parentId: r.parentId ?? null, agenceId: r.agenceId ?? null,
      badge: (e as Segment).badge ?? 'ETOILE' };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  enregistrer(): void {
    const segment = this.type() === 'SEGMENT';
    const url = segment ? '/api/segments' : '/api/referentiels';
    const corps = segment
      ? { code: this.edition.code.trim(), libelle: this.edition.libelle.trim(), badge: this.edition.badge }
      : { type: this.type(), code: this.edition.code.trim() || null, libelle: this.edition.libelle.trim(), parentId: this.edition.parentId,
          agenceId: this.edition.agenceId };
    const c = this.cible();
    this.enCours.set(true);
    this.erreur.set(null);
    (c ? this.api.putSilencieux<Referentiel>(`${url}/${c.id}`, corps) : this.api.postSilencieux<Referentiel>(url, corps)).subscribe({
      next: r => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Enregistré', detail: r.libelle });
        this.charger();
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.'); }
    });
  }

  changerStatut(e: Referentiel | Segment, action: 'archivage' | 'reactivation'): void {
    const url = this.type() === 'SEGMENT' ? '/api/segments' : '/api/referentiels';
    this.api.post<Referentiel>(`${url}/${e.id}/${action}`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: action === 'archivage' ? 'Archivé' : 'Réactivé', detail: r.libelle });
      this.charger();
    });
  }

  private charger(): void {
    this.entrees.set(null);
    if (this.type() === 'SEGMENT') {
      this.api.get<Segment[]>('/api/segments', { archives: this.archives() }).subscribe(s => this.entrees.set(s));
    } else {
      this.api.get<Referentiel[]>('/api/referentiels', { type: this.type(), archives: this.archives() }).subscribe(r => this.entrees.set(r));
    }
  }
}
