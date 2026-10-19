import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Agence, CampagneMarketing, Information } from '../../core/modeles';
import { JourPipe, LIBELLES } from '../../core/format';
import { Referentiels } from '../../core/referentiels';
import { CampagneMarketingFormulaire } from '../campagnes-marketing/campagne-marketing-formulaire';

type Edition = Omit<Information, 'id' | 'agence' | 'statut' | 'expiree' | 'nbCampagnes' | 'derniereCampagne' | 'derniereCampagneStatut'
  | 'creePar' | 'modifiePar' | 'modifieLe'>;

const LIVRAISON = ['RETARD_LIVRAISON', 'MODIFICATION_TOURNEE', 'ANNULATION_TOURNEE', 'REPRISE_LIVRAISON'];
const GARDE = ['INFORMATION_GARDE', 'INFORMATION_JOUR_FERIE'];

/**
 * Informations clients et alertes operationnelles (reprises d'UbiSenderPro) : problemes de livraison, gardes et jours feries,
 * horaires, fermeture, alerte, retrait de produit. Chaque information s'annonce par une campagne programmee.
 */
@Component({
  selector: 'app-informations',
  imports: [FormsModule, DialogModule, JourPipe, CampagneMarketingFormulaire],
  templateUrl: './informations.html'
})
export class Informations implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly ref = inject(Referentiels);
  protected readonly L = LIBELLES;
  protected readonly types = Object.entries(LIBELLES.typeInformation);
  protected readonly priorites = Object.entries(LIBELLES.prioriteInformation);
  protected readonly onglets = [
    { code: 'TOUTES', libelle: 'En cours' },
    { code: 'LIVRAISON', libelle: 'Problèmes de livraison' },
    { code: 'GARDE', libelle: 'Gardes et jours fériés' },
    { code: 'AUTRES', libelle: 'Horaires, fermetures et alertes' },
    { code: 'ARCHIVES', libelle: 'Archivées' }
  ];

  protected readonly informations = signal<Information[]>([]);
  protected readonly agences = signal<Agence[]>([]);
  protected readonly onglet = signal('TOUTES');
  protected readonly visibles = computed(() => {
    const o = this.onglet();
    return this.informations().filter(i => o === 'TOUTES' || o === 'ARCHIVES' || (o === 'LIVRAISON' ? LIVRAISON.includes(i.typeInformation)
      : o === 'GARDE' ? GARDE.includes(i.typeInformation) : !LIVRAISON.includes(i.typeInformation) && !GARDE.includes(i.typeInformation)));
  });
  protected readonly ouvert = signal(false);
  protected readonly cible = signal<Information | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  /** Campagne d'annonce : formulaire des campagnes, l'information pre-choisie. */
  protected readonly annonce = signal(false);
  protected readonly aAnnoncer = signal<number | null>(null);
  edition: Edition = vide();

  ngOnInit(): void {
    this.ref.agences.subscribe(a => this.agences.set(a));
    this.charger();
  }

  choisirOnglet(code: string): void {
    const archives = this.onglet() === 'ARCHIVES';
    this.onglet.set(code);
    if (archives !== (code === 'ARCHIVES')) this.charger();
  }

  protected livraison(): boolean { return LIVRAISON.includes(this.edition.typeInformation); }
  protected garde(): boolean { return GARDE.includes(this.edition.typeInformation); }

  nouveau(): void {
    this.cible.set(null);
    this.edition = vide();
    const o = this.onglet();
    if (o === 'GARDE') this.edition.typeInformation = 'INFORMATION_GARDE';
    if (o === 'AUTRES') this.edition.typeInformation = 'INFORMATION_GENERALE';
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  modifier(i: Information): void {
    this.cible.set(i);
    const { id, agence, statut, expiree, nbCampagnes, derniereCampagne, derniereCampagneStatut, creePar, modifiePar, modifieLe, ...reste } = i;
    this.edition = { ...reste };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  enregistrer(): void {
    const corps: Record<string, unknown> = { ...this.edition };
    for (const [k, v] of Object.entries(corps)) if (v === '') corps[k] = null;
    const i = this.cible();
    this.enCours.set(true);
    this.erreur.set(null);
    (i ? this.api.putSilencieux<Information>(`/api/marketing/informations/${i.id}`, corps)
       : this.api.postSilencieux<Information>('/api/marketing/informations', corps)).subscribe({
      next: r => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Information enregistrée', detail: r.titre });
        this.charger();
      },
      error: x => {
        this.enCours.set(false);
        this.erreur.set(x?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  action(i: Information, action: 'archivage' | 'reactivation' | 'duplication'): void {
    const libelle = { archivage: 'Information archivée', reactivation: 'Information réactivée', duplication: 'Information dupliquée' }[action];
    this.api.post<Information>(`/api/marketing/informations/${i.id}/${action}`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: libelle, detail: r.titre });
      if (action === 'duplication') this.modifier(r);
      this.charger();
    });
  }

  annoncer(i: Information): void {
    this.aAnnoncer.set(i.id);
    this.annonce.set(true);
  }

  creee(c: CampagneMarketing): void {
    this.router.navigate(['/marketing/campagnes', c.id]);
  }

  private charger(): void {
    this.api.get<Information[]>('/api/marketing/informations', { archives: this.onglet() === 'ARCHIVES' }).subscribe(i => this.informations.set(i));
  }
}

function vide(): Edition {
  return { titre: '', typeInformation: 'RETARD_LIVRAISON', priorite: 'NORMALE', message: '', agenceId: null, tournee: '', signataire: '',
    finValidite: '', dateLivraison: '', creneau: '', heureInitiale: '', nouvelleHeure: '', causeCommunicable: '', causeInterne: '',
    dateResolution: '', jourFerie: '', dateGarde: '', heureLimiteCommande: '', consignesLivraison: '', pharmacienGarde: '', telephonePharmacien: '' };
}
