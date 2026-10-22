import { espaceListes } from '../../core/listes-module';
import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Referentiels } from '../../core/referentiels';
import { ApercuListe, ListeDiffusion, Segment, ValeursCriteres } from '../../core/modeles';

/** Numeros de la fiche client qui recoivent les campagnes. */
export const FONCTIONS: { code: string; libelle: string }[] = [
  { code: 'PRINCIPAL', libelle: 'WhatsApp principal' },
  { code: 'TITULAIRE', libelle: 'Titulaire' },
  { code: 'ASSISTANT', libelle: 'Assistant' },
  { code: 'CHARGE_COMMANDE', libelle: 'Chargé des commandes' },
  { code: 'COMPTABLE', libelle: 'Comptable' },
  { code: 'SECONDAIRE', libelle: 'Autre numéro' }
];

interface Formulaire {
  nom: string; description: string; parCriteres: boolean; agenceIds: Set<number>; sousAgences: string[]; villes: string[];
  segments: Set<string>; fonctions: Set<string>;
}

/** Creation ou modification d'une liste ; le nombre de clients vises se recalcule a chaque changement. */
@Component({
  selector: 'app-liste-formulaire',
  imports: [FormsModule, AsyncPipe, DialogModule],
  templateUrl: './liste-formulaire.html'
})
export class ListeFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly ref = inject(Referentiels);
  private readonly espace = espaceListes();
  protected readonly FONCTIONS = FONCTIONS;

  readonly ouvert = model(false);
  readonly liste = input<ListeDiffusion | null>(null);
  readonly enregistre = output<ListeDiffusion>();

  protected readonly segments = this.api.get<Segment[]>('/api/clients/segments');
  protected readonly valeurs = this.api.get<ValeursCriteres>(this.espace.api + '/criteres');
  protected readonly apercu = signal<ApercuListe | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = vide();

  private readonly simulation = new Subject<void>();

  constructor() {
    this.simulation.pipe(debounceTime(300), switchMap(() => this.api.postSilencieux<ApercuListe>(this.espace.api + '/simulation', this.corps())))
      .subscribe({ next: a => this.apercu.set(a), error: () => this.apercu.set(null) });
  }

  preparer(): void {
    const l = this.liste();
    this.erreur.set(null);
    this.formulaire = l ? {
      nom: l.nom, description: l.description ?? '', parCriteres: l.parCriteres, agenceIds: new Set(l.agenceIds), sousAgences: [...l.sousAgences],
      villes: [...l.villes], segments: new Set(l.segments), fonctions: new Set(l.fonctions)
    } : vide();
    this.changer();
  }

  /** Simulation sans les ajouts et exclusions faits a la main : elle ne porte que sur les criteres. */
  changer(): void {
    this.simulation.next();
  }

  basculer<T>(ensemble: Set<T>, valeur: T, coche: boolean): void {
    if (coche) ensemble.add(valeur); else ensemble.delete(valeur);
    this.changer();
  }

  ajouter(liste: string[], valeur: string): void {
    if (valeur && !liste.includes(valeur)) liste.push(valeur);
    this.changer();
  }

  retirer(liste: string[], valeur: string): void {
    liste.splice(liste.indexOf(valeur), 1);
    this.changer();
  }

  enregistrer(): void {
    const l = this.liste();
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = l ? this.api.putSilencieux<ListeDiffusion>(`${this.espace.api}/${l.id}`, this.corps())
      : this.api.postSilencieux<ListeDiffusion>(this.espace.api, this.corps());
    requete.subscribe({
      next: liste => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: l ? 'Liste enregistrée' : 'Liste créée', detail: liste.nom });
        this.enregistre.emit(liste);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  private corps() {
    const f = this.formulaire;
    return {
      nom: f.nom.trim(), description: f.description, parCriteres: f.parCriteres, agenceIds: [...f.agenceIds], sousAgences: f.sousAgences,
      villes: f.villes, segments: [...f.segments], fonctions: [...f.fonctions]
    };
  }
}

function vide(): Formulaire {
  return { nom: '', description: '', parCriteres: true, agenceIds: new Set(), sousAgences: [], villes: [], segments: new Set(), fonctions: new Set(['PRINCIPAL']) };
}
