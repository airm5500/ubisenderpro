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

@Component({
  selector: 'app-clients',
  imports: [FormsModule, AsyncPipe, RouterLink, MontantPipe, TelephonePipe],
  templateUrl: './clients.html'
})
export class Clients implements OnInit {
  private readonly api = inject(Api);
  protected readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);

  criteres = { q: '', agenceId: null as number | null, agentId: null as number | null, sansAgent: false, avecSolde: false };
  protected readonly page = signal<Page<PortefeuilleClient> | null>(null);
  protected agents: Observable<Agent[]> = of([]);
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

  private charger(): void {
    this.api.get<Page<PortefeuilleClient>>('/api/recouvrement/portefeuille', { ...this.criteres, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
