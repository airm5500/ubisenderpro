import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AsyncPipe } from '@angular/common';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Referentiels } from '../../core/referentiels';
import { CompteProvisoire, CompteUtilisateur, Habilitation, Page, ReferentielHabilitation } from '../../core/modeles';
import { JourPipe, TelephonePipe } from '../../core/format';
import { HistoriqueUtilisateur } from './historique-utilisateur';

interface Formulaire {
  login: string; nomComplet: string; email: string; numeroWhatsapp: string;
  habilitations: { moduleCode: string; roleCode: string }[]; agenceIds: Set<number>;
}

type Fenetre = 'compte' | 'mot-de-passe' | 'desactivation' | null;

/** Roles qui voient toutes les agences : DG, Direction du Recouvrement, Directeur d'agence. */
const ROLES_TOUTES_AGENCES = ['DG', 'DIR_RECOUVREMENT', 'DIR_AGENCE'];

@Component({
  selector: 'app-utilisateurs',
  imports: [TooltipModule, FormsModule, RouterLink, AsyncPipe, DialogModule, JourPipe, TelephonePipe, HistoriqueUtilisateur],
  templateUrl: './utilisateurs.html'
})
export class Utilisateurs implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly ref = inject(Referentiels);
  protected readonly agences = signal<{ id: number; libelle: string }[]>([]);

  protected readonly page = signal<Page<CompteUtilisateur> | null>(null);
  protected readonly referentiel = signal<ReferentielHabilitation>({ roles: [], modules: [] });
  criteres = { q: '', roleCode: '', agenceId: null as number | null, statut: '' };
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  protected readonly fenetre = signal<Fenetre>(null);
  protected readonly historique = signal(false);
  protected readonly historiqueDe = signal<CompteUtilisateur | null>(null);

  voirHistorique(u: CompteUtilisateur): void {
    this.historiqueDe.set(u);
    this.historique.set(true);
  }
  protected readonly enCours = signal(false);
  protected cible: CompteUtilisateur | null = null;
  protected provisoire: CompteProvisoire | null = null;
  formulaire: Formulaire = vide();

  ngOnInit(): void {
    this.api.get<ReferentielHabilitation>('/api/utilisateurs/referentiel').subscribe(r => this.referentiel.set(r));
    this.ref.agences.subscribe(a => this.agences.set(a));
    this.rechercher();
  }

  rechercher(): void {
    this.numeroPage = 0;
    this.charger();
  }

  allerA(decalage: number): void {
    this.numeroPage = Math.min(Math.max(0, this.numeroPage + decalage), this.nbPages() - 1);
    this.charger();
  }

  nouveau(): void {
    this.cible = null;
    this.formulaire = vide();
    this.fenetre.set('compte');
  }

  modifier(u: CompteUtilisateur): void {
    this.cible = u;
    this.formulaire = {
      login: u.login, nomComplet: u.nomComplet, email: u.email ?? '', numeroWhatsapp: u.numeroWhatsapp ?? '',
      habilitations: u.habilitations.map(h => ({ moduleCode: h.moduleCode ?? '', roleCode: h.roleCode })),
      agenceIds: new Set(u.agences.map(a => a.agenceId))
    };
    this.fenetre.set('compte');
  }

  /** Modules qui n'ont pas encore de role ; aucun si un role couvre deja « Tous les modules ». */
  protected modulesLibres(): string[] {
    const pris = this.formulaire.habilitations.map(h => h.moduleCode || '');
    return pris.includes('') ? [] : this.referentiel().modules.map(m => m.code).filter(m => !pris.includes(m));
  }

  ajouterRole(): void {
    const libre = this.modulesLibres()[0];
    if (libre) this.formulaire.habilitations.push({ moduleCode: libre, roleCode: '' });
  }

  /** Les directions et le Marketing agissent sur toutes les agences : elles sont alors toutes cochees (decision du 22/10/2026). */
  roleChoisi(): void {
    const toutes = this.formulaire.habilitations.some(h => ROLES_TOUTES_AGENCES.includes(h.roleCode) || h.moduleCode === 'MARKETING');
    if (toutes) this.toutesLesAgences(true);
  }

  toutesLesAgences(cocher: boolean): void {
    const ids = this.agences().map(a => a.id);
    this.formulaire.agenceIds = cocher ? new Set(ids) : new Set();
  }

  retirerRole(i: number): void {
    this.formulaire.habilitations.splice(i, 1);
  }

  basculerAgence(id: number): void {
    const a = this.formulaire.agenceIds;
    a.has(id) ? a.delete(id) : a.add(id);
  }

  /** Un role a vision globale voit toutes les agences : le choix des agences ne sert alors a rien. */
  protected visionGlobale(): boolean {
    return this.formulaire.habilitations.some(h => this.referentiel().roles.find(r => r.code === h.roleCode)?.visionGlobale);
  }

  enregistrer(): void {
    const f = this.formulaire;
    const corps = {
      login: f.login.trim(), nomComplet: f.nomComplet.trim(), email: f.email.trim() || null, numeroWhatsapp: f.numeroWhatsapp.trim() || null,
      habilitations: f.habilitations.filter(h => h.roleCode).map(h => ({ roleCode: h.roleCode, moduleCode: h.moduleCode || null })),
      agenceIds: [...f.agenceIds]
    };
    this.enCours.set(true);
    const fini = () => this.enCours.set(false);
    if (this.cible) {
      this.api.put<CompteUtilisateur>(`/api/utilisateurs/${this.cible.id}`, corps).subscribe({
        next: u => {
          fini();
          this.fenetre.set(null);
          this.messages.add({ severity: 'success', summary: 'Compte enregistré', detail: u.nomComplet });
          this.charger();
        },
        error: fini
      });
    } else {
      this.api.post<CompteProvisoire>('/api/utilisateurs', corps).subscribe({
        next: c => { fini(); this.montrerProvisoire(c); },
        error: fini
      });
    }
  }

  reinitialiser(u: CompteUtilisateur): void {
    this.api.post<CompteProvisoire>(`/api/utilisateurs/${u.id}/reinitialisation-mot-de-passe`).subscribe(c => this.montrerProvisoire(c));
  }

  demanderDesactivation(u: CompteUtilisateur): void {
    this.cible = u;
    this.fenetre.set('desactivation');
  }

  desactiver(): void {
    this.api.post<CompteUtilisateur>(`/api/utilisateurs/${this.cible!.id}/desactivation`).subscribe(u => {
      this.fenetre.set(null);
      this.messages.add({ severity: 'success', summary: 'Compte désactivé', detail: u.nomComplet });
      this.charger();
    });
  }

  reactiver(u: CompteUtilisateur): void {
    this.api.post<CompteUtilisateur>(`/api/utilisateurs/${u.id}/reactivation`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: 'Compte réactivé', detail: r.nomComplet });
      this.charger();
    });
  }

  copier(): void {
    navigator.clipboard?.writeText(this.provisoire?.motDePasseProvisoire ?? '').then(
      () => this.messages.add({ severity: 'success', summary: 'Mot de passe copié' }),
      () => undefined
    );
  }

  libelleHabilitation(h: Habilitation): string {
    return `${h.roleLibelle} · ${h.moduleLibelle ?? 'tous les modules'}`;
  }

  private montrerProvisoire(c: CompteProvisoire): void {
    this.provisoire = c;
    this.fenetre.set('mot-de-passe');
    this.charger();
  }

  private charger(): void {
    this.api.get<Page<CompteUtilisateur>>('/api/utilisateurs', { ...this.criteres, page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }

  protected get numero(): number { return this.numeroPage; }

  protected readonly statuts: Partial<Record<string, { libelle: string; couleur: string }>> = {
    ACTIF: { libelle: 'Actif', couleur: '#51D78E' },
    DESACTIVE: { libelle: 'Désactivé', couleur: '#D64545' },
    ARCHIVE: { libelle: 'Archivé', couleur: '#9DB0BF' }
  };
}

function vide(): Formulaire {
  return { login: '', nomComplet: '', email: '', numeroWhatsapp: '', habilitations: [{ moduleCode: 'RECOUVREMENT', roleCode: 'AGENT' }], agenceIds: new Set() };
}
