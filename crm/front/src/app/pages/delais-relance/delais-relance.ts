import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { DelaiRelance } from '../../core/modeles';
import { JourPipe } from '../../core/format';

interface Edition { agenceId: number | null; segment: string; avantJoursMax: number | null; avantJoursMin: number | null; retardJoursMin: number | null; intervalleJours: number | null; }

/** Delais de relance par agence et par segment client ; une case vide reprend le niveau au-dessus. */
@Component({
  selector: 'app-delais-relance',
  imports: [TooltipModule, FormsModule, AsyncPipe, DialogModule, JourPipe],
  templateUrl: './delais-relance.html'
})
export class DelaisRelance implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);

  protected readonly defauts = signal<DelaiRelance | null>(null);
  /** Vrai quand la fenetre modifie les delais par defaut plutot qu'une regle. */
  protected readonly standard = signal(false);
  protected readonly regles = signal<DelaiRelance[]>([]);
  protected readonly archives = signal(false);
  protected readonly ouvert = signal(false);
  protected readonly cible = signal<DelaiRelance | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  edition: Edition = vide();

  private readonly toutesAgences = toSignal(this.ref.agences, { initialValue: [] });
  /** Agences que l'utilisateur peut regler : toutes en vision globale, sinon les siennes. */
  protected readonly agences = computed(() => {
    const u = this.auth.utilisateur();
    return u?.visionGlobale ? this.toutesAgences() : this.toutesAgences().filter(a => u?.agenceIds?.includes(a.id));
  });

  ngOnInit(): void {
    this.charger();
  }

  basculerArchives(): void {
    this.archives.set(!this.archives());
    this.charger();
  }

  nouvelle(): void {
    this.standard.set(false);
    this.cible.set(null);
    this.edition = vide();
    if (!this.auth.utilisateur()?.visionGlobale) this.edition.agenceId = this.agences()[0]?.id ?? null;
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  /** Les delais par defaut (parametres « Campagnes ») se modifient ici aussi, sans quitter l'ecran. */
  modifierStandard(): void {
    const d = this.defauts();
    if (!d) return;
    this.cible.set(null);
    this.standard.set(true);
    this.edition = { agenceId: null, segment: '', avantJoursMax: d.avantJoursMax, avantJoursMin: d.avantJoursMin,
      retardJoursMin: d.retardJoursMin, intervalleJours: d.intervalleJours };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  modifier(r: DelaiRelance): void {
    this.standard.set(false);
    this.cible.set(r);
    this.edition = { agenceId: r.agenceId, segment: r.segment ?? '', avantJoursMax: r.avantJoursMax, avantJoursMin: r.avantJoursMin,
      retardJoursMin: r.retardJoursMin, intervalleJours: r.intervalleJours };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  private enregistrerStandard(): void {
    const e = this.edition;
    const valeurs: [string, number | null][] = [['campagne.avant_echeance_jours_max', e.avantJoursMax], ['campagne.avant_echeance_jours_min', e.avantJoursMin],
      ['campagne.apres_echeance_jours_min', e.retardJoursMin], ['campagne.relance_intervalle_jours', e.intervalleJours]];
    if (valeurs.some(([, v]) => v === null || v === undefined || `${v}` === '')) {
      this.erreur.set('Les délais par défaut sont tous obligatoires : ils s\'appliquent quand aucune règle ne les remplace.');
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.api.putSilencieux('/api/parametres', valeurs.map(([cle, v]) => ({ cle, valeur: `${v}` }))).subscribe({
      next: () => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Délais par défaut enregistrés', detail: 'Ils s\'appliquent à toutes les agences et à tous les segments sans règle propre.' });
        this.charger();
      },
      error: err => {
        this.enCours.set(false);
        this.erreur.set(err?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  enregistrer(): void {
    if (this.standard()) {
      this.enregistrerStandard();
      return;
    }
    const corps = { ...this.edition, segment: this.edition.segment || null };
    const r = this.cible();
    this.enCours.set(true);
    this.erreur.set(null);
    (r ? this.api.putSilencieux<DelaiRelance>(`/api/recouvrement/delais-relance/${r.id}`, corps)
       : this.api.postSilencieux<DelaiRelance>('/api/recouvrement/delais-relance', corps)).subscribe({
      next: d => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Délais enregistrés', detail: this.cibleTexte(d) });
        this.charger();
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  changerStatut(r: DelaiRelance, action: 'archivage' | 'reactivation'): void {
    this.api.post<DelaiRelance>(`/api/recouvrement/delais-relance/${r.id}/${action}`).subscribe(d => {
      this.messages.add({ severity: 'success', summary: action === 'archivage' ? 'Règle archivée' : 'Règle réactivée', detail: this.cibleTexte(d) });
      this.charger();
    });
  }

  protected cibleTexte(r: DelaiRelance): string {
    return `${r.agenceLibelle ?? 'Toutes les agences'} · ${r.segmentLibelle ?? 'tous les segments'}`;
  }

  private charger(): void {
    this.api.get<DelaiRelance>('/api/recouvrement/delais-relance/defauts').subscribe(d => this.defauts.set(d));
    this.api.get<DelaiRelance[]>('/api/recouvrement/delais-relance', { archives: this.archives() }).subscribe(r => this.regles.set(r));
  }
}

function vide(): Edition {
  return { agenceId: null, segment: '', avantJoursMax: null, avantJoursMin: null, retardJoursMin: null, intervalleJours: null };
}
