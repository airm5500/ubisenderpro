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
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    if (this.auth.pilotage()) this.agents = this.ref.agents();
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

  private charger(): void {
    this.api.get<Page<MessageRecu>>('/api/recouvrement/reponses', { clientId: this.clientId(), agentId: this.agentId, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
