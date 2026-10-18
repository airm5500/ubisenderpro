import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Observable, of } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agent, Client, Segment } from '../../core/modeles';

/** Memes types de numeros que l'import des clients ; le principal sert aux relances. */
const NUMEROS: { code: string; libelle: string }[] = [
  { code: 'PRINCIPAL', libelle: 'WhatsApp principal (relances)' },
  { code: 'TITULAIRE', libelle: 'Titulaire' },
  { code: 'ASSISTANT', libelle: 'Assistant' },
  { code: 'CHARGE_COMMANDE', libelle: 'Chargé des commandes' },
  { code: 'COMPTABLE', libelle: 'Comptable' },
  { code: 'SECONDAIRE', libelle: 'Autre numéro' }
];

interface Formulaire {
  code: string; raisonSociale: string; nomPharmacien: string; agenceId: number | null; sousAgence: string; segmentation: string;
  ville: string; adresse: string; email: string; commentaire: string; numeros: Record<string, string>; agentId: number | null;
}

/** Saisie d'un client arrive seul (creation) ou correction de sa fiche (modification). */
@Component({
  selector: 'app-client-formulaire',
  imports: [FormsModule, AsyncPipe, DialogModule],
  templateUrl: './client-formulaire.html'
})
export class ClientFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);
  protected readonly NUMEROS = NUMEROS;

  readonly ouvert = model(false);
  /** Client a modifier ; absent pour une creation. */
  readonly client = input<Client | null>(null);
  readonly enregistre = output<Client>();

  protected readonly segments = this.api.get<Segment[]>('/api/clients/segments');
  protected agents: Observable<Agent[]> = of([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = vide();

  /** Appele a l'ouverture : repart de la fiche, ou d'un formulaire vierge. */
  preparer(): void {
    const c = this.client();
    this.erreur.set(null);
    this.formulaire = c ? {
      code: c.code, raisonSociale: c.raisonSociale, nomPharmacien: c.nomPharmacien ?? '', agenceId: c.agenceId, sousAgence: c.sousAgence ?? '',
      segmentation: c.segmentation ?? '', ville: c.ville ?? '', adresse: c.adresse ?? '', email: c.email ?? '', commentaire: c.commentaire ?? '',
      numeros: Object.fromEntries(NUMEROS.map(n => [n.code, local(c.numeros?.[n.code])])), agentId: null
    } : vide();
    this.agenceChoisie();
  }

  agenceChoisie(): void {
    this.formulaire.agentId = null;
    this.agents = !this.client() && this.auth.peut('PORTEFEUILLES_AFFECTER') && this.formulaire.agenceId
      ? this.ref.agents(this.formulaire.agenceId) : of([]);
  }

  enregistrer(): void {
    const f = this.formulaire;
    const corps = {
      code: f.code.trim(), raisonSociale: f.raisonSociale.trim(), nomPharmacien: f.nomPharmacien, agenceId: f.agenceId,
      sousAgence: f.sousAgence, segmentation: f.segmentation || null, ville: f.ville, adresse: f.adresse, email: f.email, commentaire: f.commentaire,
      numeros: Object.fromEntries(Object.entries(f.numeros).filter(([, v]) => v.trim()))
    };
    const c = this.client();
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = c ? this.api.putSilencieux<Client>(`/api/clients/${c.id}`, corps) : this.api.postSilencieux<Client>('/api/clients', corps);
    requete.subscribe({
      next: client => {
        if (!c && f.agentId) {
          this.api.post('/api/recouvrement/affectations', { clientIds: [client.id], agentId: f.agentId, motif: 'Nouveau client' })
            .subscribe({ next: () => this.termine(client, true), error: () => this.termine(client, false) });
        } else {
          this.termine(client, false);
        }
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  private termine(client: Client, affecte: boolean): void {
    this.enCours.set(false);
    this.ouvert.set(false);
    this.messages.add({
      severity: 'success', summary: this.client() ? 'Client enregistré' : 'Client créé',
      detail: client.raisonSociale + (affecte ? ' · agent affecté' : '')
    });
    this.enregistre.emit(client);
  }
}

function vide(): Formulaire {
  return {
    code: '', raisonSociale: '', nomPharmacien: '', agenceId: null, sousAgence: '', segmentation: '', ville: '', adresse: '', email: '',
    commentaire: '', numeros: Object.fromEntries(NUMEROS.map(n => [n.code, ''])), agentId: null
  };
}

/** 2250700000001 -> 0700000001 : on saisit et relit le numero comme on le dicte. */
function local(numero: string | undefined): string {
  if (!numero) return '';
  return numero.length > 10 ? numero.substring(numero.length - 10) : numero;
}
