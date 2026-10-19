import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Droit, RoleGestion } from '../../core/modeles';
import { JourPipe } from '../../core/format';

interface Groupe { titre: string; droits: Droit[]; }
interface Formulaire { libelle: string; description: string; visionGlobale: boolean; droits: Set<string>; modele: number | null; }

/** Le role Administrateur garde toujours ces deux ecrans (meme regle cote serveur). */
const VERROUS_ADMIN = ['UTILISATEURS', 'ROLES'];

@Component({
  selector: 'app-roles',
  imports: [TooltipModule, FormsModule, DialogModule, JourPipe],
  templateUrl: './roles.html'
})
export class Roles implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);

  protected readonly roles = signal<RoleGestion[] | null>(null);
  protected readonly catalogue = signal<Droit[]>([]);
  /** Droits ranges comme le menu, par rubrique. */
  protected readonly groupes = computed<Groupe[]>(() => {
    const groupes: Groupe[] = [];
    for (const d of this.catalogue()) {
      let g = groupes.find(x => x.titre === d.groupe);
      if (!g) groupes.push(g = { titre: d.groupe, droits: [] });
      g.droits.push(d);
    }
    return groupes;
  });

  protected readonly ouvert = signal(false);
  protected readonly enCours = signal(false);
  protected cible: RoleGestion | null = null;
  formulaire: Formulaire = vide();

  ngOnInit(): void {
    this.api.get<Droit[]>('/api/roles/droits').subscribe(d => this.catalogue.set(d));
    this.charger();
  }

  nouveau(): void {
    this.cible = null;
    this.formulaire = vide();
    this.ouvert.set(true);
  }

  modifier(r: RoleGestion): void {
    this.cible = r;
    this.formulaire = { libelle: r.libelle, description: r.description ?? '', visionGlobale: r.visionGlobale, droits: new Set(r.droits), modele: null };
    this.ouvert.set(true);
  }

  /** Nouveau role : reprendre les cases d'un role existant, puis les ajuster. */
  partirDe(id: number | null): void {
    const r = this.roles()?.find(x => x.id === id);
    if (r) {
      this.formulaire.droits = new Set(r.droits);
      this.formulaire.visionGlobale = r.visionGlobale;
    }
  }

  verrouille(code: string): boolean {
    return this.cible?.code === 'ADMIN' && VERROUS_ADMIN.includes(code);
  }

  basculer(code: string): void {
    if (this.verrouille(code)) return;
    const d = this.formulaire.droits;
    d.has(code) ? d.delete(code) : d.add(code);
  }

  toutCoche(g: Groupe): boolean {
    return g.droits.every(d => this.formulaire.droits.has(d.code));
  }

  partiel(g: Groupe): boolean {
    return !this.toutCoche(g) && g.droits.some(d => this.formulaire.droits.has(d.code));
  }

  basculerGroupe(g: Groupe): void {
    const cocher = !this.toutCoche(g);
    for (const d of g.droits) {
      if (this.verrouille(d.code)) continue;
      cocher ? this.formulaire.droits.add(d.code) : this.formulaire.droits.delete(d.code);
    }
  }

  /** Les comptes qui ont ce role se reconnectent quand ses droits changent : l'ecran le dit avant d'enregistrer. */
  protected changeDroits(): boolean {
    const r = this.cible;
    if (!r) return false;
    const f = this.formulaire;
    return f.visionGlobale !== r.visionGlobale || f.droits.size !== r.droits.length || r.droits.some(d => !f.droits.has(d));
  }

  protected estMonRole(): boolean {
    return !!this.cible && this.auth.roles().includes(this.cible.code);
  }

  enregistrer(): void {
    const f = this.formulaire;
    // Ordre du catalogue, pour un journal lisible
    const corps = {
      libelle: f.libelle.trim(), description: f.description.trim() || null, visionGlobale: f.visionGlobale,
      droits: this.catalogue().map(d => d.code).filter(c => f.droits.has(c))
    };
    this.enCours.set(true);
    const fini = () => this.enCours.set(false);
    const requete = this.cible ? this.api.put<RoleGestion>(`/api/roles/${this.cible.id}`, corps) : this.api.post<RoleGestion>('/api/roles', corps);
    requete.subscribe({
      next: r => {
        fini();
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: this.cible ? 'Rôle enregistré' : 'Rôle créé', detail: r.libelle });
        this.charger();
      },
      error: fini
    });
  }

  desactiver(r: RoleGestion): void {
    this.api.post<RoleGestion>(`/api/roles/${r.id}/desactivation`).subscribe(x => {
      this.messages.add({ severity: 'success', summary: 'Rôle désactivé', detail: x.libelle });
      this.charger();
    });
  }

  reactiver(r: RoleGestion): void {
    this.api.post<RoleGestion>(`/api/roles/${r.id}/reactivation`).subscribe(x => {
      this.messages.add({ severity: 'success', summary: 'Rôle réactivé', detail: x.libelle });
      this.charger();
    });
  }

  /** « 12 écrans sur 20 » pour la liste. */
  resume(r: RoleGestion): string {
    const n = r.droits.length;
    return `${n} droit${n > 1 ? 's' : ''} sur ${this.catalogue().length}`;
  }

  libellesDroits(r: RoleGestion): string {
    return this.catalogue().filter(d => r.droits.includes(d.code)).map(d => d.libelle).join(', ');
  }

  private charger(): void {
    this.api.get<RoleGestion[]>('/api/roles').subscribe(r => this.roles.set(r));
  }
}

function vide(): Formulaire {
  return { libelle: '', description: '', visionGlobale: false, droits: new Set(), modele: null };
}
