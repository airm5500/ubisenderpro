import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Api } from './api';
import { Agence, Agent, Segment, SousType } from './modeles';

/** Listes de reference chargees une fois par session. */
@Injectable({ providedIn: 'root' })
export class Referentiels {
  private readonly api = inject(Api);

  readonly agences: Observable<Agence[]> = this.api.get<Agence[]>('/api/agences').pipe(shareReplay(1));
  readonly segments: Observable<Segment[]> = this.api.get<Segment[]>('/api/clients/segments').pipe(shareReplay(1));
  readonly sousTypes: Observable<SousType[]> = this.api.get<SousType[]>('/api/recouvrement/sous-types').pipe(shareReplay(1));

  /** Agents de recouvrement (profils de pilotage seulement), rafraichis a chaque appel. */
  agents(agenceId?: number | null): Observable<Agent[]> {
    return this.api.get<Agent[]>('/api/recouvrement/agents', { agenceId });
  }
}
