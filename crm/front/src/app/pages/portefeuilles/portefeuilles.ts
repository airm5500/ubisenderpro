import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agent, Delegation, Page, PortefeuilleClient, ResultatAffectation } from '../../core/modeles';
import { JourPipe, MontantPipe, aujourdhui, TelephonePipe } from '../../core/format';

type Fenetre = 'affecter' | 'retirer' | 'transfert' | 'numero' | 'interim' | null;

@Component({
  selector: 'app-portefeuilles',
  imports: [FormsModule, AsyncPipe, RouterLink, DialogModule, JourPipe, MontantPipe, TelephonePipe],
  templateUrl: './portefeuilles.html'
})
export class Portefeuilles implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);

  readonly q = input<string>();

  protected readonly agents = signal<Agent[]>([]);
  protected readonly delegations = signal<Delegation[]>([]);
  protected readonly page = signal<Page<PortefeuilleClient> | null>(null);
  protected readonly selection = signal<Set<number>>(new Set());
  criteres = { q: '', agenceId: null as number | null, agentId: null as number | null, sansAgent: false, avecSolde: false };
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  protected readonly fenetre = signal<Fenetre>(null);
  protected agentCible: Agent | null = null;
  formulaire = { agentId: null as number | null, motif: '', numero: '', remplacantId: null as number | null, dateDebut: aujourdhui(), dateFin: aujourdhui(7) };

  ngOnInit(): void {
    this.criteres.q = this.q() ?? '';
    this.chargerAgents();
    this.chargerDelegations();
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.selection.set(new Set());
    this.chargerClients();
  }

  voirAgent(a: Agent): void {
    this.criteres = { ...this.criteres, agentId: a.id, sansAgent: false, q: '' };
    this.rechercher();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.chargerClients();
  }

  basculer(id: number): void {
    const s = new Set(this.selection());
    s.has(id) ? s.delete(id) : s.add(id);
    this.selection.set(s);
  }

  toutBasculer(coche: boolean): void {
    this.selection.set(coche ? new Set((this.page()?.elements ?? []).map(c => c.clientId)) : new Set());
  }

  ouvrir(f: Fenetre, agent: Agent | null = null): void {
    this.agentCible = agent;
    this.formulaire = { agentId: null, motif: '', numero: agent?.numeroWhatsapp ?? '', remplacantId: null, dateDebut: aujourdhui(), dateFin: aujourdhui(7) };
    this.fenetre.set(f);
  }

  confirmer(): void {
    const f = this.formulaire;
    const clientIds = [...this.selection()];
    switch (this.fenetre()) {
      case 'affecter':
        this.api.post<ResultatAffectation>('/api/recouvrement/affectations', { clientIds, agentId: f.agentId, motif: f.motif })
          .subscribe(r => this.apresAffectation(r));
        break;
      case 'retirer':
        this.api.post<ResultatAffectation>('/api/recouvrement/affectations/retrait', { clientIds, motif: f.motif })
          .subscribe(r => this.apresAffectation(r));
        break;
      case 'transfert':
        this.api.post<ResultatAffectation>(`/api/recouvrement/agents/${this.agentCible!.id}/transfert`, { versAgentId: f.agentId, motif: f.motif })
          .subscribe(r => this.apresAffectation(r));
        break;
      case 'numero':
        this.api.put(`/api/recouvrement/agents/${this.agentCible!.id}/numero`, { numeroWhatsapp: f.numero }).subscribe(() => {
          this.messages.add({ severity: 'success', summary: 'Numéro enregistré', detail: this.agentCible!.nomComplet });
          this.fenetre.set(null);
          this.chargerAgents();
        });
        break;
      case 'interim':
        this.api.post('/api/recouvrement/delegations', {
          titulaireId: this.agentCible!.id, remplacantId: f.remplacantId, dateDebut: f.dateDebut, dateFin: f.dateFin, motif: f.motif
        }).subscribe(() => {
          this.messages.add({ severity: 'success', summary: 'Intérim enregistré', detail: this.agentCible!.nomComplet });
          this.fenetre.set(null);
          this.chargerAgents();
          this.chargerDelegations();
        });
        break;
    }
  }

  annulerInterim(d: Delegation): void {
    this.api.post(`/api/recouvrement/delegations/${d.id}/annulation`).subscribe(() => {
      this.chargerDelegations();
      this.chargerAgents();
    });
  }

  private apresAffectation(r: ResultatAffectation): void {
    this.fenetre.set(null);
    const detail = `${r.nbTraites} client(s) traité(s), ${r.nbInchanges} inchangé(s).` + (r.refus.length ? ' ' + r.refus.join(' ') : '');
    this.messages.add({ severity: r.refus.length ? 'warn' : 'success', summary: 'Portefeuilles mis à jour', detail, life: 8000 });
    this.chargerAgents();
    this.rechercher();
  }

  private chargerAgents(): void {
    this.ref.agents().subscribe(a => this.agents.set(a));
  }

  private chargerDelegations(): void {
    this.api.get<Delegation[]>('/api/recouvrement/delegations', { enCours: true }).subscribe(d => this.delegations.set(d));
  }

  private chargerClients(): void {
    this.api.get<Page<PortefeuilleClient>>('/api/recouvrement/portefeuille', { ...this.criteres, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }

  protected readonly titres: Partial<Record<string, string>> = {
    affecter: 'Affecter les clients choisis', retirer: 'Retirer les clients de leur agent', transfert: 'Transférer tout le portefeuille',
    numero: 'Numéro WhatsApp professionnel', interim: 'Organiser un intérim'
  };
}
