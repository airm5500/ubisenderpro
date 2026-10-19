import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agence, ListeDiffusion } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { FONCTIONS, ListeFormulaire } from './liste-formulaire';

@Component({
  selector: 'app-listes-diffusion',
  imports: [FormsModule, AsyncPipe, RouterLink, JourPipe, ListeFormulaire],
  templateUrl: './listes-diffusion.html'
})
export class ListesDiffusion implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);

  protected readonly listes = signal<ListeDiffusion[] | null>(null);
  protected readonly saisie = signal(false);
  protected readonly cible = signal<ListeDiffusion | null>(null);
  avecArchivees = false;

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.get<ListeDiffusion[]>('/api/marketing/listes', { avecArchivees: this.avecArchivees }).subscribe(l => this.listes.set(l));
  }

  nouvelle(): void {
    this.cible.set(null);
    this.saisie.set(true);
  }

  modifier(l: ListeDiffusion): void {
    this.cible.set(l);
    this.saisie.set(true);
  }

  enregistree(l: ListeDiffusion): void {
    if (this.cible()) this.charger(); else this.router.navigate(['/marketing/listes', l.id]);
  }

  archiver(l: ListeDiffusion): void {
    this.api.post(`/api/marketing/listes/${l.id}/archivage`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Liste archivée', detail: l.nom });
      this.charger();
    });
  }

  reactiver(l: ListeDiffusion): void {
    this.api.post(`/api/marketing/listes/${l.id}/reactivation`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Liste réactivée', detail: l.nom });
      this.charger();
    });
  }

  /** "Abidjan Nord, Platinum · 2 villes" : les criteres en une ligne. */
  resume(l: ListeDiffusion, agences: Agence[]): string {
    if (!l.parCriteres) return 'Clients choisis à la main';
    const morceaux = [
      ...l.agenceIds.map(id => agences.find(a => a.id === id)?.libelle ?? '?'),
      ...l.segments,
      ...l.sousAgences.map(s => 'Sous-agence ' + s),
      ...l.villes
    ];
    return morceaux.length ? morceaux.join(', ') : 'Tous les clients du périmètre';
  }

  numeros(l: ListeDiffusion): string {
    return l.fonctions.map(f => FONCTIONS.find(x => x.code === f)?.libelle ?? f).join(', ');
  }
}
