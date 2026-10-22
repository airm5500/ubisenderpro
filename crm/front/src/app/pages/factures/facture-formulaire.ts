import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { Api } from '../../core/api';
import { Referentiels } from '../../core/referentiels';
import { Client, Facture, Page } from '../../core/modeles';
import { aujourdhui } from '../../core/format';

interface Formulaire {
  reference: string; clientId: number | null; sousTypeId: number | null; nature: string;
  dateFacture: string; dateEcheance: string; montant: number | null; solde: number | null;
}

/** Facture ou avoir arrive entre deux imports (creation), ou correction et reglement (modification). */
@Component({
  selector: 'app-facture-formulaire',
  imports: [FormsModule, AsyncPipe, DialogModule],
  templateUrl: './facture-formulaire.html'
})
export class FactureFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly ref = inject(Referentiels);

  readonly ouvert = model(false);
  /** Facture a modifier ; absente pour une creation. */
  readonly facture = input<Facture | null>(null);
  /** Client impose (creation depuis la fiche client). */
  readonly client = input<Client | null>(null);
  readonly enregistre = output<Facture>();

  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly clients = signal<Client[]>([]);
  protected readonly clientChoisi = signal<{ id: number; libelle: string } | null>(null);
  protected recherche = '';
  private readonly saisie = new Subject<string>();
  formulaire: Formulaire = vide();

  constructor() {
    this.saisie.pipe(
      debounceTime(250), distinctUntilChanged(),
      switchMap(q => q.trim().length < 2 ? of(null) : this.api.get<Page<Client>>('/api/clients', { q: q.trim(), page: 0, taille: 8 }))
    ).subscribe(p => this.clients.set(p?.elements ?? []));
  }

  preparer(): void {
    const f = this.facture();
    const c = this.client();
    this.erreur.set(null);
    this.recherche = '';
    this.clients.set([]);
    if (f) {
      this.formulaire = {
        reference: f.reference, clientId: f.clientId, sousTypeId: f.sousTypeId, nature: f.nature,
        dateFacture: f.dateFacture, dateEcheance: f.dateEcheance, montant: Math.abs(f.montant), solde: Math.abs(f.solde)
      };
      this.clientChoisi.set({ id: f.clientId, libelle: `${f.clientRaisonSociale} (${f.clientCode})` });
    } else {
      this.formulaire = vide();
      this.formulaire.clientId = c?.id ?? null;
      this.clientChoisi.set(c ? { id: c.id, libelle: `${c.raisonSociale} (${c.code})` } : null);
    }
  }

  chercher(q: string): void {
    this.saisie.next(q);
  }

  choisir(c: Client): void {
    this.formulaire.clientId = c.id;
    this.clientChoisi.set({ id: c.id, libelle: `${c.raisonSociale} (${c.code})` });
    this.clients.set([]);
  }

  changerClient(): void {
    this.formulaire.clientId = null;
    this.clientChoisi.set(null);
  }

  /** Une facture neuve est due en entier : le solde suit le montant tant qu'on ne l'a pas change. */
  montantSaisi(m: number | null): void {
    if (!this.facture() && (this.formulaire.solde === null || this.formulaire.solde === this.precedent)) this.formulaire.solde = m;
    this.precedent = m;
  }
  private precedent: number | null = null;

  protected pret(): boolean {
    const f = this.formulaire;
    return !!f.reference.trim() && !!f.clientId && !!f.sousTypeId && !!f.dateFacture && !!f.dateEcheance && f.montant !== null && f.solde !== null;
  }

  enregistrer(): void {
    const f = this.formulaire;
    const corps = { ...f, reference: f.reference.trim() };
    const existante = this.facture();
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = existante
      ? this.api.putSilencieux<Facture>(`/api/recouvrement/factures/${existante.id}`, corps)
      : this.api.postSilencieux<Facture>('/api/recouvrement/factures', corps);
    requete.subscribe({
      next: facture => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: existante ? 'Facture enregistrée' : (facture.nature === 'AVOIR' ? 'Avoir créé' : 'Facture créée'),
          detail: `${facture.reference} · ${facture.clientRaisonSociale}` });
        this.enregistre.emit(facture);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}

function vide(): Formulaire {
  return { reference: '', clientId: null, sousTypeId: null, nature: 'FACTURE', dateFacture: aujourdhui(), dateEcheance: '', montant: null, solde: null };
}
