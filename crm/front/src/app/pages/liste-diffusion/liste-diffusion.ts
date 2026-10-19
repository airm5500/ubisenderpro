import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ApercuListe, Client, ListeDiffusion, MembreListe, Page } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { ListeFormulaire } from '../listes-diffusion/liste-formulaire';

interface Onglet { code: 'TOUS' | 'JOIGNABLES' | 'NON_JOIGNABLES' | 'EXCLUS'; libelle: string; }
const ONGLETS: Onglet[] = [
  { code: 'TOUS', libelle: 'Tous' }, { code: 'JOIGNABLES', libelle: 'Joignables' },
  { code: 'NON_JOIGNABLES', libelle: 'Non joignables' }, { code: 'EXCLUS', libelle: 'Exclus à la main' }
];

@Component({
  selector: 'app-liste-diffusion',
  imports: [FormsModule, RouterLink, JourPipe, ListeFormulaire],
  templateUrl: './liste-diffusion.html'
})
export class ListeDiffusionDetail implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  protected readonly auth = inject(Auth);
  protected readonly onglets = ONGLETS;

  /** Parametre de route. */
  readonly id = input.required<string>();

  protected readonly liste = signal<ListeDiffusion | null>(null);
  protected readonly apercu = signal<ApercuListe | null>(null);
  protected readonly onglet = signal<Onglet>(ONGLETS[0]);
  protected readonly saisie = signal(false);
  protected readonly modifiable = computed(() => this.auth.peut('LISTES_GERER') && this.liste()?.statut === 'ACTIF');
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.apercu()?.membres.total ?? 0) / this.taille)));
  protected readonly trouves = signal<Client[] | null>(null);
  q = '';
  recherche = '';
  numero = 0;
  private readonly taille = 25;
  private readonly frappe = new Subject<string>();

  constructor() {
    this.frappe.pipe(
      debounceTime(250), distinctUntilChanged(),
      switchMap(q => q.trim().length < 2 ? of(null) : this.api.get<Page<Client>>('/api/clients', { q: q.trim(), page: 0, taille: 8 }))
    ).subscribe(p => this.trouves.set(p?.elements ?? null));
  }

  ngOnInit(): void {
    this.chargerListe();
    this.charger();
  }

  chargerListe(): void {
    this.api.get<ListeDiffusion>(`/api/marketing/listes/${this.id()}`).subscribe(l => this.liste.set(l));
  }

  charger(): void {
    this.api.get<ApercuListe>(`/api/marketing/listes/${this.id()}/membres`, { q: this.q, filtre: this.onglet().code, page: this.numero, taille: this.taille })
      .subscribe(a => this.apercu.set(a));
  }

  rechercherMembres(): void {
    this.numero = 0;
    this.charger();
  }

  choisirOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.rechercherMembres();
  }

  allerA(decalage: number): void {
    this.numero = Math.min(Math.max(0, this.numero + decalage), this.nbPages() - 1);
    this.charger();
  }

  chercherClient(q: string): void {
    this.frappe.next(q);
  }

  ajouter(c: Client): void {
    this.changer([c.id], 'AJOUT', `${c.raisonSociale} ajouté à la liste`);
    this.recherche = '';
    this.trouves.set(null);
  }

  exclure(m: MembreListe): void {
    this.changer([m.clientId], 'EXCLUSION', `${m.raisonSociale} exclu de la liste`);
  }

  /** Annule un ajout ou une exclusion faits a la main : le client suit de nouveau les criteres. */
  retirer(m: MembreListe): void {
    this.changer([m.clientId], 'RETRAIT', m.origine === 'EXCLUSION' ? `${m.raisonSociale} réintégré` : `${m.raisonSociale} retiré de la liste`);
  }

  desabonner(m: MembreListe): void {
    this.confirmation.confirm({
      header: 'Désabonner ce client',
      message: `${m.raisonSociale} ne recevra plus aucune campagne marketing, quelle que soit la liste. Les relances du recouvrement continuent.`,
      acceptLabel: 'Désabonner', rejectLabel: 'Annuler',
      accept: () => this.api.post(`/api/marketing/clients/${m.clientId}/desabonnement`, { motif: 'Demande du client' }).subscribe(() => {
        this.messages.add({ severity: 'success', summary: 'Client désabonné', detail: m.raisonSociale });
        this.rafraichir();
      })
    });
  }

  reabonner(m: MembreListe): void {
    this.api.post(`/api/marketing/clients/${m.clientId}/reabonnement`).subscribe(() => {
      this.messages.add({ severity: 'success', summary: 'Client réabonné', detail: m.raisonSociale });
      this.rafraichir();
    });
  }

  enregistree(l: ListeDiffusion): void {
    this.liste.set(l);
    this.rechercherMembres();
  }

  private changer(clientIds: number[], sens: string, detail: string): void {
    this.api.post<ListeDiffusion>(`/api/marketing/listes/${this.id()}/clients`, { clientIds, sens }).subscribe(l => {
      this.liste.set(l);
      this.messages.add({ severity: 'success', summary: 'Liste mise à jour', detail });
      this.charger();
    });
  }

  private rafraichir(): void {
    this.chargerListe();
    this.charger();
  }
}
