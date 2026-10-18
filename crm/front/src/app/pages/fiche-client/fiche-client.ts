import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Affectation, Client, Facture, MessageRecu, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, TelephonePipe } from '../../core/format';
import { ClientFormulaire } from '../clients/client-formulaire';

interface Evenement { quand: string; qui: string; titre: string; detail: string; couleur: string; }

const PALETTE = ['#05253D', '#51D78E', '#0B5C8E', '#F4B740', '#9DB0BF'];

@Component({
  selector: 'app-fiche-client',
  imports: [RouterLink, JourPipe, MontantPipe, TelephonePipe, ClientFormulaire],
  templateUrl: './fiche-client.html',
  styleUrl: './fiche-client.css'
})
export class FicheClient implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  readonly id = input.required<string>();
  protected readonly client = signal<Client | null>(null);
  protected readonly modification = signal(false);
  protected readonly factures = signal<Facture[]>([]);
  protected readonly voirSoldees = signal(false);
  protected readonly reponses = signal<MessageRecu[]>([]);
  protected readonly affectations = signal<Affectation[]>([]);

  protected readonly ouvertes = computed(() => this.factures().filter(f => f.statut !== 'SOLDEE' && f.solde !== 0));
  protected readonly soldees = computed(() => this.factures().filter(f => f.statut === 'SOLDEE' || f.solde === 0));
  protected readonly totalDu = computed(() => this.ouvertes().reduce((s, f) => s + f.solde, 0));
  protected readonly agentEnCours = computed(() => this.affectations().find(a => !a.dateFin) ?? null);

  /** Repartition du restant du par sous-type de compte. */
  protected readonly parSousType = computed(() => {
    const total = this.totalDu();
    const sommes = new Map<string, number>();
    for (const f of this.ouvertes()) sommes.set(f.sousTypeLibelle, (sommes.get(f.sousTypeLibelle) ?? 0) + f.solde);
    return [...sommes.entries()].map(([libelle, montant], i) => ({
      libelle, montant, couleur: PALETTE[i % PALETTE.length], part: total > 0 ? Math.max(0, montant / total * 100) : 0
    }));
  });

  /** Fil chronologique : reponses du client et changements d'agent. */
  protected readonly historique = computed<Evenement[]>(() => {
    const ev: Evenement[] = [
      ...this.reponses().map(r => ({
        quand: r.recuLe, qui: `reçu sur ${r.numeroAgent}${r.agentNom ? ' · ' + r.agentNom : ''}`,
        titre: r.campagneNom ? `Réponse à « ${r.campagneNom} »` : 'Message du client',
        detail: r.contenu ? `« ${r.contenu} »` : `[${r.type}]`, couleur: '#51D78E'
      })),
      ...this.affectations().map(a => ({
        quand: a.dateDebut, qui: a.affectePar ?? '', titre: `Affecté à ${a.agentNom}`,
        detail: [a.motifDebut, a.dateFin ? `jusqu'au ${a.dateFin.substring(0, 10).split('-').reverse().join('/')}${a.motifFin ? ' (' + a.motifFin + ')' : ''}` : 'en cours'].filter(Boolean).join(' · '),
        couleur: '#05253D'
      }))
    ];
    return ev.sort((a, b) => b.quand.localeCompare(a.quand));
  });

  ngOnInit(): void {
    const id = Number(this.id());
    this.api.get<Client>(`/api/clients/${id}`).subscribe(c => this.client.set(c));
    this.api.toutes<Facture>('/api/recouvrement/factures', { clientId: id }, 100).subscribe(f => this.factures.set(f));
    this.api.get<Page<MessageRecu>>('/api/recouvrement/reponses', { clientId: id, page: 0, taille: 50 }).subscribe(p => this.reponses.set(p.elements));
    if (this.auth.peut('PORTEFEUILLES_AFFECTER')) {
      this.api.get<Affectation[]>(`/api/recouvrement/clients/${id}/affectations`).subscribe(a => this.affectations.set(a));
    }
  }
}
