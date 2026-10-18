import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Campagne, Page, Recapitulatif } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, resumeCriteres, TelephonePipe } from '../../core/format';

interface Onglet { statut: string; libelle: string; }

@Component({
  selector: 'app-campagnes',
  imports: [FormsModule, RouterLink, JourPipe, MontantPipe, TelephonePipe],
  templateUrl: './campagnes.html'
})
export class Campagnes implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;
  protected readonly resume = resumeCriteres;

  protected readonly onglets: Onglet[] = [
    { statut: 'A_VALIDER', libelle: 'À valider' }, { statut: 'VALIDEE', libelle: 'Programmées' }, { statut: 'EN_COURS', libelle: 'En cours' },
    { statut: 'TERMINEE', libelle: 'Exécutées' }, { statut: 'ANNULEE', libelle: 'Annulées' }, { statut: 'BROUILLON', libelle: 'Brouillons' },
    { statut: '', libelle: 'Toutes' }
  ];
  protected readonly onglet = signal('');
  protected readonly compteurs = signal<Record<string, number>>({});
  protected readonly page = signal<Page<Campagne> | null>(null);
  protected readonly choisie = signal<Campagne | null>(null);
  protected readonly recap = signal<Recapitulatif | null>(null);
  q = '';
  motifRejet = '';
  protected readonly rejetOuvert = signal(false);
  private numeroPage = 0;
  private readonly taille = 25;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    this.compter(true);
  }

  choisirOnglet(statut: string): void {
    this.onglet.set(statut);
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  ouvrir(c: Campagne): void {
    this.choisie.set(c);
    this.recap.set(null);
    this.rejetOuvert.set(false);
    this.motifRejet = '';
    if (c.statut === 'A_VALIDER' || c.statut === 'BROUILLON') {
      this.api.get<Recapitulatif>(`/api/recouvrement/campagnes/${c.id}/recapitulatif`).subscribe(r => this.recap.set(r));
    }
  }

  valider(c: Campagne): void {
    this.api.post<Campagne>(`/api/recouvrement/campagnes/${c.id}/validation`).subscribe(m => {
      this.messages.add({ severity: 'success', summary: 'Campagne validée', detail: m.dateExecution ? `Envoi prévu le ${m.dateExecution.replace('T', ' à ').substring(0, 19)}.` : 'Les envois démarrent.' });
      this.apresAction();
    });
  }

  rejeter(c: Campagne): void {
    if (!this.motifRejet.trim()) return;
    this.api.post<Campagne>(`/api/recouvrement/campagnes/${c.id}/rejet`, { motif: this.motifRejet.trim() }).subscribe(() => {
      this.messages.add({ severity: 'info', summary: 'Campagne renvoyée', detail: `${c.creePar} la retrouve dans ses brouillons avec votre motif.` });
      this.apresAction();
    });
  }

  private apresAction(): void {
    this.choisie.set(null);
    this.compter(false);
  }

  private compter(choisirOnglet: boolean): void {
    const requetes = Object.fromEntries(this.onglets.map(o =>
      [o.statut || 'TOUTES', this.api.get<Page<Campagne>>('/api/recouvrement/campagnes', { statut: o.statut, page: 0, taille: 1 })]));
    forkJoin(requetes).subscribe(r => {
      const c = Object.fromEntries(Object.entries(r).map(([k, p]) => [k, p.total]));
      this.compteurs.set(c);
      if (choisirOnglet) this.onglet.set(this.auth.peut('CAMPAGNES_VALIDER') && c['A_VALIDER'] > 0 ? 'A_VALIDER' : '');
      this.charger();
    });
  }

  private charger(): void {
    this.api.get<Page<Campagne>>('/api/recouvrement/campagnes', { q: this.q, statut: this.onglet(), page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
  }

  protected get numero(): number { return this.numeroPage; }
}
