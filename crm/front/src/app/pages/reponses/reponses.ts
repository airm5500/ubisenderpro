import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, of } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agent, MessageRecu, Page } from '../../core/modeles';
import { JourPipe, TelephonePipe } from '../../core/format';

/** Reponses des clients : une ligne par client, la plus recente en haut ; un clic ouvre son fil dans l'ordre chronologique. */
@Component({
  selector: 'app-reponses',
  imports: [FormsModule, AsyncPipe, RouterLink, JourPipe, TelephonePipe],
  templateUrl: './reponses.html'
})
export class Reponses implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);
  private readonly ref = inject(Referentiels);

  readonly clientId = input<string>();
  agentId: number | null = null;
  protected agents: Observable<Agent[]> = of([]);
  protected readonly page = signal<Page<MessageRecu> | null>(null);
  /** Cle du client ouvert (fiche ou numero inconnu) et son fil. */
  protected readonly ouvert = signal<string | null>(null);
  protected readonly fil = signal<MessageRecu[] | null>(null);
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    if (this.auth.voitAgents()) this.agents = this.ref.agents();
    this.charger();
  }

  filtrer(): void {
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  protected cle(r: MessageRecu): string {
    return r.clientId ? 'c' + r.clientId : 'n' + r.numeroClient;
  }

  basculer(r: MessageRecu): void {
    const cle = this.cle(r);
    if (this.ouvert() === cle) {
      this.ouvert.set(null);
      return;
    }
    this.ouvert.set(cle);
    this.fil.set(null);
    this.api.get<Page<MessageRecu>>('/api/recouvrement/reponses', {
      clientId: r.clientId, numeroClient: r.clientId ? null : r.numeroClient, agentId: this.agentId, chronologique: true, taille: 200
    }).subscribe(p => this.ouvert() === cle && this.fil.set(p.elements));
  }

  private charger(): void {
    this.ouvert.set(null);
    this.api.get<Page<MessageRecu>>('/api/recouvrement/reponses/par-client', { clientId: this.clientId(), agentId: this.agentId, page: this.numeroPage, taille: this.taille })
      .subscribe(p => {
        this.page.set(p);
        // Arrivee depuis la fiche d'un client : son fil s'ouvre directement
        if (this.clientId() && p.elements.length === 1) this.basculer(p.elements[0]);
      });
  }

  protected get numero(): number { return this.numeroPage; }
}
