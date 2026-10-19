import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Campagne, MessageCampagne, Page, Recapitulatif } from '../../core/modeles';
import { JourPipe, LIBELLES, MontantPipe, resumeCriteres, TelephonePipe } from '../../core/format';

type Action = 'rejet' | 'annulation' | null;

@Component({
  selector: 'app-campagne-suivi',
  imports: [TooltipModule, FormsModule, RouterLink, DialogModule, JourPipe, MontantPipe, TelephonePipe],
  templateUrl: './campagne-suivi.html'
})
export class CampagneSuivi implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly messagesService = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;
  protected readonly resume = resumeCriteres;

  readonly id = input.required<string>();
  protected readonly campagne = signal<Campagne | null>(null);
  protected readonly recap = signal<Recapitulatif | null>(null);
  protected readonly page = signal<Page<MessageCampagne> | null>(null);
  protected readonly ouvert = signal<MessageCampagne | null>(null);
  protected readonly filtre = signal('');
  protected readonly action = signal<Action>(null);
  motif = '';
  q = '';
  private numeroPage = 0;
  private readonly taille = 25;
  private minuterie: ReturnType<typeof setInterval> | null = null;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  protected readonly avantValidation = computed(() => ['BROUILLON', 'A_VALIDER'].includes(this.campagne()?.statut ?? ''));
  protected readonly estAuteur = computed(() => this.campagne()?.creeParId === this.auth.utilisateur()?.id);
  protected readonly filtres = computed(() => this.avantValidation()
    ? [{ code: '', libelle: 'À envoyer' }, { code: 'EXCLU', libelle: 'Exclues' }]
    : [{ code: '', libelle: 'Tous' }, { code: 'A_ENVOYER', libelle: 'En attente' }, { code: 'ENVOYE', libelle: 'Envoyés' },
       { code: 'DISTRIBUE', libelle: 'Distribués' }, { code: 'LU', libelle: 'Lus' }, { code: 'ECHEC', libelle: 'Échecs' },
       { code: 'EXCLU', libelle: 'Exclues' }, { code: 'ANNULE', libelle: 'Annulés' }]);

  ngOnInit(): void {
    this.charger();
    // Une campagne en cours avance au rythme des envois : rafraichissement regulier
    this.minuterie = setInterval(() => { if (this.campagne()?.statut === 'EN_COURS') this.charger(false); }, 20000);
  }

  ngOnDestroy(): void {
    if (this.minuterie) clearInterval(this.minuterie);
  }

  choisirFiltre(code: string): void {
    this.filtre.set(code);
    this.numeroPage = 0;
    this.chargerMessages();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.chargerMessages();
  }

  rechercher(): void {
    this.numeroPage = 0;
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

  private agir(chemin: string, corps: object, titre: string): void {
    this.api.post<Campagne>(`/api/recouvrement/campagnes/${this.id()}/${chemin}`, corps).subscribe(c => {
      this.messagesService.add({ severity: 'success', summary: titre, detail: c.nom });
      this.action.set(null);
      this.motif = '';
      this.charger();
    });
  }

  private charger(complet = true): void {
    this.api.get<Campagne>(`/api/recouvrement/campagnes/${this.id()}`).subscribe(c => {
      this.campagne.set(c);
      if (complet && ['BROUILLON', 'A_VALIDER'].includes(c.statut)) {
        this.api.get<Recapitulatif>(`/api/recouvrement/campagnes/${c.id}/recapitulatif`).subscribe(r => this.recap.set(r));
      }
      this.chargerMessages();
    });
  }

  private chargerMessages(): void {
    this.api.get<Page<MessageCampagne>>(`/api/recouvrement/campagnes/${this.id()}/messages`,
      { statut: this.filtre(), q: this.q, page: this.numeroPage, taille: this.taille }).subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }
}
