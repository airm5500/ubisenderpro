import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { CampagneMarketing, MessageCampagne, Page, RecapitulatifMarketing } from '../../core/modeles';
import { JourPipe, LIBELLES, TelephonePipe } from '../../core/format';
import { CampagneMarketingFormulaire } from '../campagnes-marketing/campagne-marketing-formulaire';
import { EnvoyerMaintenant } from '../../core/envoyer-maintenant';

type Action = 'rejet' | 'annulation' | null;

/** Controle avant validation, puis suivi des envois d'une campagne promotionnelle. */
@Component({
  selector: 'app-campagne-marketing',
  imports: [TooltipModule, FormsModule, RouterLink, DialogModule, JourPipe, TelephonePipe, CampagneMarketingFormulaire, EnvoyerMaintenant],
  templateUrl: './campagne-marketing.html'
})
export class CampagneMarketingSuivi implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly messagesService = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  readonly id = input.required<string>();
  protected readonly campagne = signal<CampagneMarketing | null>(null);
  protected readonly recap = signal<RecapitulatifMarketing | null>(null);
  protected readonly page = signal<Page<MessageCampagne> | null>(null);
  protected readonly ouvert = signal<MessageCampagne | null>(null);
  protected readonly filtre = signal('');
  protected readonly action = signal<Action>(null);
  protected readonly saisie = signal(false);
  motif = '';
  q = '';
  numero = 0;
  private readonly taille = 25;
  private minuterie: ReturnType<typeof setInterval> | null = null;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  protected readonly avantValidation = computed(() => ['BROUILLON', 'A_VALIDER'].includes(this.campagne()?.statut ?? ''));
  protected readonly estAuteur = computed(() => this.campagne()?.creeParId === this.auth.utilisateur()?.id);
  protected readonly valideur = computed(() => this.auth.peut('CAMPAGNES_MARKETING_VALIDER'));
  protected readonly peutModifier = computed(() => this.avantValidation() && (this.estAuteur() || this.valideur()));
  protected readonly filtres = computed(() => this.avantValidation()
    ? [{ code: '', libelle: 'Tous' }, { code: 'A_ENVOYER', libelle: 'À envoyer' }, { code: 'EXCLU', libelle: 'Exclus' }]
    : [{ code: '', libelle: 'Tous' }, { code: 'A_ENVOYER', libelle: 'En attente' }, { code: 'ENVOYE', libelle: 'Envoyés' },
       { code: 'DISTRIBUE', libelle: 'Distribués' }, { code: 'LU', libelle: 'Lus' }, { code: 'ECHEC', libelle: 'Échecs' },
       { code: 'EXCLU', libelle: 'Exclus' }, { code: 'ANNULE', libelle: 'Annulés' }]);

  ngOnInit(): void {
    this.charger();
    this.minuterie = setInterval(() => { if (this.campagne()?.statut === 'EN_COURS') this.charger(); }, 20000);
  }

  ngOnDestroy(): void {
    if (this.minuterie) clearInterval(this.minuterie);
  }

  choisirFiltre(code: string): void {
    this.filtre.set(code);
    this.rechercher();
  }

  rechercher(): void {
    this.numero = 0;
    this.chargerMessages();
  }

  allerA(decalage: number): void {
    this.numero = Math.min(Math.max(0, this.numero + decalage), this.nbPages() - 1);
    this.chargerMessages();
  }

  soumettre(): void { this.agir('soumission', {}, 'Campagne soumise à validation'); }
  valider(): void { this.agir('validation', {}, 'Campagne validée'); }
  lancer(): void { this.agir('lancement', {}, 'Envois démarrés'); }

  confirmerAction(): void {
    const a = this.action();
    if (!a || !this.motif.trim()) return;
    this.agir(a, { motif: this.motif.trim() }, a === 'rejet' ? 'Campagne renvoyée à son auteur' : 'Campagne annulée');
  }

  charger(): void {
    this.api.get<CampagneMarketing>(`/api/marketing/campagnes/${this.id()}`).subscribe(c => {
      this.campagne.set(c);
      this.api.get<RecapitulatifMarketing>(`/api/marketing/campagnes/${c.id}/recapitulatif`).subscribe(r => this.recap.set(r));
      this.chargerMessages();
    });
  }

  private agir(chemin: string, corps: object, titre: string): void {
    this.api.post<CampagneMarketing>(`/api/marketing/campagnes/${this.id()}/${chemin}`, corps).subscribe(c => {
      this.messagesService.add({ severity: 'success', summary: titre, detail: c.nom });
      this.action.set(null);
      this.motif = '';
      this.charger();
    });
  }

  private chargerMessages(): void {
    this.api.get<Page<MessageCampagne>>(`/api/marketing/campagnes/${this.id()}/messages`,
      { statut: this.filtre(), q: this.q, page: this.numero, taille: this.taille }).subscribe(p => this.page.set(p));
  }
}
