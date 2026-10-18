import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Import, Page } from '../../core/modeles';
import { JourPipe } from '../../core/format';

@Component({
  selector: 'app-imports',
  imports: [FormsModule, RouterLink, JourPipe],
  templateUrl: './imports.html'
})
export class Imports implements OnInit {
  private readonly api = inject(Api);
  protected readonly auth = inject(Auth);

  type = '';
  protected readonly page = signal<Page<Import> | null>(null);
  private numeroPage = 0;
  private readonly taille = 25;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));
  protected readonly statuts: Partial<Record<string, string>> = { SIMULE: 'Simulé, non validé', VALIDE: 'Validé', ANNULE: 'Abandonné' };
  protected readonly couleurs: Partial<Record<string, string>> = { SIMULE: '#F4B740', VALIDE: '#51D78E', ANNULE: '#9DB0BF' };

  ngOnInit(): void {
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

  rejets(i: Import): void {
    this.api.telecharger(`/api/imports/${i.id}/rejets`, `rejets-${i.id}.csv`);
  }

  private charger(): void {
    this.api.get<Page<Import>>('/api/imports', { type: this.type, page: this.numeroPage, taille: this.taille }).subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
