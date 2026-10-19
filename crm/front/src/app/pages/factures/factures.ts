import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Facture, Page } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, aujourdhui } from '../../core/format';
import { FactureFormulaire } from './facture-formulaire';

interface Onglet { code: string; libelle: string; statut?: string; enRetard?: boolean; }

const ONGLETS: Onglet[] = [
  { code: 'TOUTES', libelle: 'Toutes' },
  { code: 'A_RELANCER', libelle: 'À relancer', statut: 'A_RELANCER' },
  { code: 'ECHUES', libelle: 'Échues', enRetard: true },
  { code: 'RELANCE_PROGRAMMEE', libelle: 'Relance programmée', statut: 'RELANCE_PROGRAMMEE' },
  { code: 'MESSAGE_LU', libelle: 'Message lu', statut: 'MESSAGE_LU' },
  { code: 'REPONSE_RECUE', libelle: 'Réponse reçue', statut: 'REPONSE_RECUE' },
  { code: 'SOLDEE', libelle: 'Soldées', statut: 'SOLDEE' }
];

@Component({
  selector: 'app-factures',
  imports: [FormsModule, AsyncPipe, RouterLink, JourPipe, MontantPipe, FactureFormulaire],
  templateUrl: './factures.html'
})
export class Factures implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);
  protected readonly L = LIBELLES;
  protected readonly onglets = ONGLETS;

  criteres = { q: '', agenceId: null as number | null, sousTypeId: null as number | null, nature: '', echeanceDu: '', echeanceAu: '' };
  protected readonly onglet = signal<Onglet>(ONGLETS[0]);
  protected readonly page = signal<Page<Facture> | null>(null);
  protected readonly compteurs = signal<Record<string, number>>({});
  protected readonly selection = signal<Map<number, Facture>>(new Map());
  protected readonly saisie = signal(false);
  protected readonly cible = signal<Facture | null>(null);
  private numeroPage = 0;
  private readonly taille = 25;

  protected readonly resumeSelection = computed(() => {
    const factures = [...this.selection().values()];
    return {
      nb: factures.length,
      clients: new Set(factures.map(f => f.clientId)).size,
      solde: factures.reduce((s, f) => s + (f.solde ?? 0), 0)
    };
  });
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
    this.compter();
  }

  reinitialiser(): void {
    this.criteres = { q: '', agenceId: null, sousTypeId: null, nature: '', echeanceDu: '', echeanceAu: '' };
    this.onglet.set(ONGLETS[0]);
    this.rechercher();
  }

  choisirOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  nouvelle(): void {
    this.cible.set(null);
    this.saisie.set(true);
  }

  modifier(f: Facture): void {
    this.cible.set(f);
    this.saisie.set(true);
  }

  enregistree(): void {
    this.charger();
    this.compter();
  }

  basculer(f: Facture): void {
    const s = new Map(this.selection());
    s.has(f.id) ? s.delete(f.id) : s.set(f.id, f);
    this.selection.set(s);
  }

  viderSelection(): void {
    this.selection.set(new Map());
  }

  toutBasculer(coche: boolean): void {
    const s = new Map(this.selection());
    for (const f of this.page()?.elements ?? []) {
      coche ? s.set(f.id, f) : s.delete(f.id);
    }
    this.selection.set(s);
  }

  toutCoche(): boolean {
    const el = this.page()?.elements ?? [];
    return el.length > 0 && el.every(f => this.selection().has(f.id));
  }

  /** Relance des clients des factures choisies : campagne personnalisee pre-remplie. */
  relancerSelection(): void {
    const clients = [...new Set([...this.selection().values()].map(f => f.clientId))];
    this.router.navigate(['/campagnes/nouvelle'], { queryParams: { clients: clients.join(',') } });
  }

  private filtres(o: Onglet) {
    return { ...this.criteres, statut: o.statut, enRetard: o.enRetard ? true : null };
  }

  private charger(): void {
    this.api.get<Page<Facture>>('/api/recouvrement/factures', { ...this.filtres(this.onglet()), page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  private compter(): void {
    const requetes = Object.fromEntries(ONGLETS.map(o =>
      [o.code, this.api.get<Page<Facture>>('/api/recouvrement/factures', { ...this.filtres(o), page: 0, taille: 1 })]));
    forkJoin(requetes).subscribe(r => this.compteurs.set(Object.fromEntries(Object.entries(r).map(([k, p]) => [k, p.total]))));
  }

  protected get numero(): number { return this.numeroPage; }
  protected readonly aujourdhui = aujourdhui();
}
