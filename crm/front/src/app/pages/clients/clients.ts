import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Observable, of } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agent, Page, PortefeuilleClient } from '../../core/modeles';
import { MontantPipe, TelephonePipe } from '../../core/format';
import { ClientFormulaire } from './client-formulaire';
import { MiseAJourSelective } from './mise-a-jour-selective';
import { VerificationNumerosDialogue } from './verification-numeros';
import { DialogModule } from 'primeng/dialog';
import { MessageService } from 'primeng/api';

import { BadgeSegment } from '../../core/badges';
@Component({
  selector: 'app-clients',
  imports: [FormsModule, AsyncPipe, RouterLink, MontantPipe, TelephonePipe, ClientFormulaire, BadgeSegment, MiseAJourSelective, VerificationNumerosDialogue, DialogModule],
  templateUrl: './clients.html'
})
export class Clients implements OnInit {
  private readonly api = inject(Api);
  protected readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);

  criteres = { q: '', agenceId: null as number | null, agentId: null as number | null, sansAgent: false, avecSolde: false, statut: 'ACTIF' };
  protected readonly page = signal<Page<PortefeuilleClient> | null>(null);
  protected agents: Observable<Agent[]> = of([]);
  protected readonly creation = signal(false);
  private readonly messages = inject(MessageService);
  protected readonly selection = signal<Set<number>>(new Set());
  protected readonly miseAJour = signal(false);
  protected readonly verification = signal(false);
  protected readonly desactivation = signal(false);
  /** Ids passes au verificateur : la selection, ou aucun pour une saisie libre. */
  protected readonly aVerifier = signal<number[]>([]);
  motif = '';
  protected readonly erreurDesactivation = signal<string | null>(null);
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    if (this.auth.voitAgents()) this.agents = this.ref.agents();
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  basculer(id: number): void {
    const s = new Set(this.selection());
    s.has(id) ? s.delete(id) : s.add(id);
    this.selection.set(s);
  }

  toutBasculer(coche: boolean): void {
    this.selection.set(coche ? new Set((this.page()?.elements ?? []).map(c => c.clientId)) : new Set());
  }

  ids(): number[] { return [...this.selection()]; }

  agencesSelection(): number[] {
    return (this.page()?.elements ?? []).filter(c => this.selection().has(c.clientId)).map(c => c.agenceId);
  }

  verifier(selection: boolean): void {
    this.aVerifier.set(selection ? this.ids() : []);
    this.verification.set(true);
  }

  ouvrirDesactivation(): void {
    this.motif = '';
    this.erreurDesactivation.set(null);
    this.desactivation.set(true);
  }

  /** Desactive les clients coches un par un ; s'arrete a la premiere erreur. */
  desactiver(): void {
    const ids = this.ids();
    let faits = 0;
    const suivant = (): void => {
      if (faits === ids.length) {
        this.desactivation.set(false);
        this.messages.add({ severity: 'success', summary: 'Clients désactivés', detail: `${faits} client(s) ne recevront plus de messages.` });
        this.charger();
        return;
      }
      this.api.postSilencieux(`/api/clients/${ids[faits]}/desactivation`, { motif: this.motif }).subscribe({
        next: () => { faits++; suivant(); },
        error: e => { this.erreurDesactivation.set(e?.error?.message ?? 'Désactivation impossible.'); if (faits) this.charger(); }
      });
    };
    suivant();
  }

  private charger(): void {
    this.selection.set(new Set());
    this.api.get<Page<PortefeuilleClient>>('/api/recouvrement/portefeuille', { ...this.criteres, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
