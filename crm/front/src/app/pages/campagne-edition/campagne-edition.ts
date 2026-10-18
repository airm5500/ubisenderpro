import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { Agence, Agent, Campagne, CampagneDemande, CriteresCampagne, EtatEmail, EtatSms, ModeleMessage, Recapitulatif, SousType } from '../../core/modeles';
import { LIBELLES, MontantPipe, aujourdhui, compterSms, resumeCriteres, TelephonePipe } from '../../core/format';

const SEGMENTS = [{ code: 'P', libelle: 'Platinum' }, { code: 'S', libelle: 'Silver' }, { code: 'B', libelle: 'Bronze' }];

@Component({
  selector: 'app-campagne-edition',
  imports: [FormsModule, RouterLink, MontantPipe, TelephonePipe],
  templateUrl: './campagne-edition.html'
})
export class CampagneEdition implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  private readonly ref = inject(Referentiels);
  protected readonly L = LIBELLES;
  protected readonly segments = SEGMENTS;
  protected readonly types = Object.entries(LIBELLES.typeCampagne);

  /** Modification d'un brouillon ; clients = relance de clients choisis depuis les factures ou la fiche. */
  readonly id = input<string>();
  readonly clients = input<string>();

  protected campagneId: number | null = null;
  nom = '';
  typeCampagne = 'APRES_ECHEANCE';
  modeleId: number | null = null;
  criteres: CriteresCampagne = this.criteresVides();
  quand: 'validation' | 'date' = 'validation';
  dateEnvoi = aujourdhui();
  heureEnvoi = '09:00';

  protected readonly agences = signal<Agence[]>([]);
  protected readonly agents = signal<Agent[]>([]);
  protected readonly sousTypes = signal<SousType[]>([]);
  protected readonly modeles = signal<ModeleMessage[]>([]);
  protected readonly recap = signal<Recapitulatif | null>(null);
  protected readonly exemple = signal(0);
  protected readonly occupe = signal(false);
  protected readonly motifRejet = signal<string | null>(null);
  /** Le recapitulatif ne correspond plus au formulaire. */
  protected readonly perime = signal(true);

  /** Le canal (WhatsApp, e-mail ou SMS) est celui du modele choisi ; on choisit d'abord le canal, puis un modele de ce canal. */
  protected readonly canal = signal('WHATSAPP');
  protected readonly etatEmail = signal<EtatEmail | null>(null);
  protected readonly etatSms = signal<EtatSms | null>(null);
  protected readonly compter = (texte: string | null) => compterSms(texte ?? '');
  protected readonly modelesDuType = computed(() => this.modeles()
    .filter(m => m.typeCampagne === this.typeSignal() && m.canal === this.canal()));
  private readonly typeSignal = signal('APRES_ECHEANCE');
  protected readonly puces = signal<string[]>([]);
  protected readonly aujourdhui = aujourdhui();

  ngOnInit(): void {
    this.ref.agences.subscribe(a => this.agences.set(a.filter(x => this.auth.utilisateur()?.visionGlobale || this.auth.utilisateur()?.agenceIds.includes(x.id))));
    this.ref.sousTypes.subscribe(s => this.sousTypes.set(s));
    if (this.auth.pilotage()) this.ref.agents().subscribe(a => this.agents.set(a));
    this.api.get<ModeleMessage[]>('/api/recouvrement/modeles-messages', { statut: 'ACTIF' }).subscribe(m => {
      this.modeles.set(m);
      if (!this.modeleId) this.choisirModeleParDefaut();
    });

    const id = Number(this.id());
    if (id) {
      this.api.get<Campagne>(`/api/recouvrement/campagnes/${id}`).subscribe(c => this.remplir(c));
    } else if (this.clients()) {
      this.typeCampagne = 'PERSONNALISEE';
      this.typeSignal.set(this.typeCampagne);
      this.criteres.clientIds = this.clients()!.split(',').map(Number).filter(Boolean);
      this.nom = `Relance de ${this.criteres.clientIds.length} client${this.criteres.clientIds.length > 1 ? 's' : ''} · ${aujourdhui().split('-').reverse().join('/')}`;
    }
    this.api.getSilencieux<EtatEmail>('/api/email/etat').subscribe({ next: e => this.etatEmail.set(e), error: () => undefined });
    this.api.getSilencieux<EtatSms>('/api/sms/etat').subscribe({ next: e => this.etatSms.set(e), error: () => undefined });
    this.modifie();
  }

  changerCanal(canal: string): void {
    this.canal.set(canal);
    this.choisirModeleParDefaut();
    this.modifie();
  }

  changerType(): void {
    this.typeSignal.set(this.typeCampagne);
    const c = this.criteres;
    if (this.typeCampagne !== 'AVANT_ECHEANCE') { c.joursAvantMin = null; c.joursAvantMax = null; }
    if (this.typeCampagne !== 'APRES_ECHEANCE' && this.typeCampagne !== 'PERSONNALISEE') { c.joursRetardMin = null; c.joursRetardMax = null; }
    if (this.typeCampagne !== 'PERSONNALISEE') { c.echeanceDu = null; c.echeanceAu = null; }
    this.choisirModeleParDefaut();
    this.modifie();
  }

  basculer(liste: 'agenceIds' | 'agentIds' | 'sousTypeIds', id: number): void {
    const l = this.criteres[liste] ?? [];
    this.criteres[liste] = l.includes(id) ? l.filter(x => x !== id) : [...l, id];
    this.modifie();
  }

  basculerSegment(code: string): void {
    const l = this.criteres.segments ?? [];
    this.criteres.segments = l.includes(code) ? l.filter(x => x !== code) : [...l, code];
    this.modifie();
  }

  retirerClients(): void {
    this.criteres.clientIds = [];
    this.modifie();
  }

  modifie(): void {
    this.perime.set(true);
    this.puces.set(resumeCriteres({ ...this.criteres, clientIds: [] }));
  }

  /** Enregistre le brouillon puis recalcule le recapitulatif et les exemples de messages. */
  calculer(): void {
    this.occupe.set(true);
    this.enregistrer().pipe(
      switchMap(c => this.api.get<Recapitulatif>(`/api/recouvrement/campagnes/${c.id}/recapitulatif`))
    ).subscribe({
      next: r => { this.recap.set(r); this.exemple.set(0); this.perime.set(false); this.occupe.set(false); },
      error: () => this.occupe.set(false)
    });
  }

  brouillon(): void {
    this.occupe.set(true);
    this.enregistrer().subscribe({
      next: c => {
        this.occupe.set(false);
        this.messages.add({ severity: 'success', summary: 'Brouillon enregistré', detail: c.nom });
        this.router.navigate(['/campagnes', c.id]);
      },
      error: () => this.occupe.set(false)
    });
  }

  soumettre(): void {
    this.occupe.set(true);
    this.enregistrer().pipe(
      switchMap(c => this.api.post<Campagne>(`/api/recouvrement/campagnes/${c.id}/soumission`))
    ).subscribe({
      next: c => {
        this.occupe.set(false);
        this.messages.add({ severity: 'success', summary: 'Campagne soumise', detail: 'La Direction du Recouvrement doit maintenant la valider.' });
        this.router.navigate(['/campagnes', c.id]);
      },
      error: () => this.occupe.set(false)
    });
  }

  exempleSuivant(): void {
    const n = this.recap()?.exemples.length ?? 0;
    if (n) this.exemple.set((this.exemple() + 1) % n);
  }

  private enregistrer(): Observable<Campagne> {
    const demande: CampagneDemande = {
      nom: this.nom.trim(), typeCampagne: this.typeCampagne, modeleId: this.modeleId, criteres: this.nettoyes(),
      dateExecution: this.quand === 'date' ? `${this.dateEnvoi}T${this.heureEnvoi}:00` : null
    };
    const requete = this.campagneId
      ? this.api.put<Campagne>(`/api/recouvrement/campagnes/${this.campagneId}`, demande)
      : this.api.post<Campagne>('/api/recouvrement/campagnes', demande);
    return requete.pipe(switchMap(c => { this.campagneId = c.id; return of(c); }));
  }

  private remplir(c: Campagne): void {
    if (c.statut !== 'BROUILLON' && c.statut !== 'A_VALIDER') {
      this.router.navigate(['/campagnes', c.id]);
      return;
    }
    this.campagneId = c.id;
    this.nom = c.nom;
    this.typeCampagne = c.typeCampagne;
    this.typeSignal.set(c.typeCampagne);
    this.modeleId = c.modeleId;
    this.canal.set(c.canal);
    this.criteres = { ...this.criteresVides(), ...c.criteres };
    this.motifRejet.set(c.motifRejet);
    if (c.dateExecution) {
      this.quand = 'date';
      this.dateEnvoi = c.dateExecution.substring(0, 10);
      this.heureEnvoi = c.dateExecution.substring(11, 16);
    }
    this.modifie();
  }

  private choisirModeleParDefaut(): void {
    const m = this.modelesDuType();
    if (!m.some(x => x.id === this.modeleId)) this.modeleId = m[0]?.id ?? null;
  }

  private nettoyes(): CriteresCampagne {
    const c = this.criteres;
    const nombre = (v: unknown) => v === '' || v === undefined || v === null ? null : Number(v);
    return {
      agenceIds: c.agenceIds, agentIds: c.agentIds, clientIds: c.clientIds, sousTypeIds: c.sousTypeIds, segments: c.segments,
      soldeMin: nombre(c.soldeMin), soldeMax: nombre(c.soldeMax), echeanceDu: c.echeanceDu || null, echeanceAu: c.echeanceAu || null,
      joursAvantMin: nombre(c.joursAvantMin), joursAvantMax: nombre(c.joursAvantMax),
      joursRetardMin: nombre(c.joursRetardMin), joursRetardMax: nombre(c.joursRetardMax)
    };
  }

  private criteresVides(): CriteresCampagne {
    return { agenceIds: [], agentIds: [], clientIds: [], sousTypeIds: [], segments: [], soldeMin: null, soldeMax: null,
      echeanceDu: null, echeanceAu: null, joursAvantMin: null, joursAvantMax: null, joursRetardMin: null, joursRetardMax: null };
  }
}
