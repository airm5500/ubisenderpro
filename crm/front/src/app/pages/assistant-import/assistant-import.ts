import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { ApercuImport, Import, ImportLigne, ModeleImport } from '../../core/modeles';

/** Ecart d'une mise a jour, lu dans le message « champ : actuel -> importe ; ... ». */
interface Ecart { champ: string; actuel: string; importe: string; }

const ETAPES = ['Fichier', 'Colonnes', 'Simulation', 'Rapport'];

/** Ce qui change d'un type d'import a l'autre : ou simuler et valider, comment nommer les lignes, ou revenir. */
interface TypeImport {
  simulation: string; pluriel: string; feminin: boolean; cle: string; retour: string; libelleRetour: string; fin: string; libelleFin: string;
}
const TYPES: Record<string, TypeImport> = {
  clients: {
    simulation: '/api/imports/clients', pluriel: 'clients', feminin: false, cle: 'Code client',
    retour: '/imports', libelleRetour: 'Imports', fin: '/clients', libelleFin: 'Voir les clients'
  },
  factures: {
    simulation: '/api/recouvrement/imports/factures', pluriel: 'factures', feminin: true, cle: 'Référence',
    retour: '/imports', libelleRetour: 'Imports', fin: '/factures', libelleFin: 'Voir les factures'
  },
  produits: {
    simulation: '/api/marketing/imports/produits', pluriel: 'produits', feminin: false, cle: 'Code produit',
    retour: '/marketing/catalogue', libelleRetour: 'Catalogue', fin: '/marketing/catalogue', libelleFin: 'Voir le catalogue'
  }
};

@Component({
  selector: 'app-assistant-import',
  imports: [FormsModule, RouterLink, DialogModule],
  templateUrl: './assistant-import.html',
  styleUrl: './assistant-import.css'
})
export class AssistantImport implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  /** clients, factures ou produits, depuis l'URL ; import = reprise d'une simulation. */
  readonly type = input<string>('factures');
  readonly import = input<string>();

  protected readonly etapes = ETAPES;
  protected readonly etape = signal(0);
  protected readonly occupe = signal(false);

  // Etape 1
  protected readonly modeles = signal<ModeleImport[]>([]);
  modeleId: number | null = null;
  protected fichier: File | null = null;
  protected readonly survol = signal(false);

  // Etape 2
  protected readonly apercu = signal<ApercuImport | null>(null);
  correspondance: Record<string, string> = {};
  nomModele = '';

  // Etape 3
  protected readonly simulation = signal<Import | null>(null);
  protected readonly misesAJour = signal<ImportLigne[]>([]);
  protected readonly rejets = signal<ImportLigne[]>([]);
  protected readonly cochees = signal<Set<string>>(new Set());
  protected readonly ligneCorrigee = signal<ImportLigne | null>(null);
  valeursCorrigees: Record<string, string> = {};

  // Etape 4
  protected readonly rapport = signal<Import | null>(null);

  protected readonly config = computed(() => TYPES[this.type()] ?? TYPES['factures']);
  protected readonly libelleType = computed(() => this.config().pluriel);
  /** Accord des adjectifs : « connus » ou « connues ». */
  protected readonly accord = computed(() => this.config().feminin ? 'e' : '');
  protected readonly manquants = computed(() => {
    const a = this.apercu();
    return a ? a.champs.filter(c => c.obligatoire && !this.correspondanceDe(c.code)).map(c => c.libelle) : [];
  });
  protected readonly motifsRejet = computed(() => {
    const compte = new Map<string, number>();
    for (const l of this.rejets()) compte.set(l.message, (compte.get(l.message) ?? 0) + 1);
    return [...compte.entries()].sort((a, b) => b[1] - a[1]);
  });

  ngOnInit(): void {
    this.api.get<ModeleImport[]>(`/api/imports/${this.type()}/modeles`).subscribe(m => this.modeles.set(m));
    const reprise = Number(this.import());
    if (reprise) {
      this.api.get<Import>(`/api/imports/${reprise}`).subscribe(i => this.afficherSimulation(i));
    }
  }

  exemplaire(): void {
    this.api.telecharger(`/api/imports/${this.type()}/exemplaire`, `exemplaire-${this.type()}.csv`);
  }

  deposer(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survol.set(false);
    const f = evenement.dataTransfer?.files?.[0];
    if (f) this.choisir(f);
  }

  choisir(f: File | null | undefined): void {
    if (!f) return;
    this.fichier = f;
    this.occupe.set(true);
    this.api.envoyerFichier<ApercuImport>(`/api/imports/${this.type()}/apercu`, f, undefined, { modeleId: this.modeleId }).subscribe({
      next: a => {
        this.apercu.set(a);
        this.correspondance = { ...a.correspondance };
        this.occupe.set(false);
        this.etape.set(1);
      },
      error: () => this.occupe.set(false)
    });
  }

  correspondanceDe(champ: string): string {
    return this.correspondance[champ] ?? '';
  }

  exemples(cle: string): string {
    return this.apercu()?.colonnes.find(c => c.cle === cle)?.exemples.join(' · ') ?? '';
  }

  enregistrerModele(): void {
    if (!this.nomModele.trim()) return;
    this.api.post<ModeleImport>(`/api/imports/${this.type()}/modeles`, { nom: this.nomModele.trim(), correspondance: this.nettoyee() }).subscribe(m => {
      this.modeles.set([...this.modeles().filter(x => x.id !== m.id), m]);
      this.messages.add({ severity: 'success', summary: 'Modèle enregistré', detail: `« ${m.nom} » sera proposé au prochain import.` });
      this.nomModele = '';
    });
  }

  simuler(): void {
    if (!this.fichier) return;
    this.occupe.set(true);
    this.api.envoyerFichier<Import>(this.config().simulation, this.fichier, { nom: 'parametres', valeur: { correspondance: this.nettoyee() } }).subscribe({
      next: i => { this.occupe.set(false); this.afficherSimulation(i); },
      error: () => this.occupe.set(false)
    });
  }

  ecarts(l: ImportLigne): Ecart[] {
    return (l.message ?? '').split(' ; ').filter(Boolean).map(e => {
      const [champ, reste = ''] = e.split(/ : (.*)/s);
      const [actuel, importe = ''] = reste.split(' -> ');
      return { champ, actuel, importe };
    });
  }

  basculer(cle: string): void {
    const s = new Set(this.cochees());
    s.has(cle) ? s.delete(cle) : s.add(cle);
    this.cochees.set(s);
  }

  toutCocher(coche: boolean): void {
    this.cochees.set(coche ? new Set(this.misesAJour().map(l => l.cle)) : new Set());
  }

  ouvrirCorrection(l: ImportLigne): void {
    this.valeursCorrigees = { ...l.donnees };
    this.ligneCorrigee.set(l);
  }

  corriger(): void {
    const l = this.ligneCorrigee();
    const s = this.simulation();
    if (!l || !s) return;
    this.api.put<Import>(`/api/imports/${s.id}/lignes/${l.id}`, { valeurs: this.valeursCorrigees }).subscribe(i => {
      this.ligneCorrigee.set(null);
      this.afficherSimulation(i);
    });
  }

  telechargerRejets(): void {
    const s = this.simulation();
    if (s) this.api.telecharger(`/api/imports/${s.id}/rejets`, `rejets-${s.id}.csv`);
  }

  abandonner(): void {
    const s = this.simulation();
    if (!s) { this.router.navigate([this.config().retour]); return; }
    this.api.post(`/api/imports/${s.id}/annulation`).subscribe(() => this.router.navigate([this.config().retour]));
  }

  valider(): void {
    const s = this.simulation();
    if (!s) return;
    const toutes = this.misesAJour().length > 0 && this.cochees().size === this.misesAJour().length;
    const url = `${this.config().simulation}/${s.id}/validation`;
    this.occupe.set(true);
    this.api.post<Import>(url, { toutesLesMisesAJour: toutes, clesAMettreAJour: toutes ? [] : [...this.cochees()] }).subscribe({
      next: i => { this.occupe.set(false); this.rapport.set(i); this.etape.set(3); },
      error: () => this.occupe.set(false)
    });
  }

  recommencer(): void {
    const cible = this.type() === 'produits' ? this.router.navigate(['/marketing/catalogue/import'])
      : this.router.navigate(['/imports/nouveau'], { queryParams: { type: this.type() } });
    cible.then(() => {
      this.etape.set(0); this.fichier = null; this.apercu.set(null); this.simulation.set(null); this.rapport.set(null);
    });
  }

  private afficherSimulation(i: Import): void {
    this.simulation.set(i);
    this.etape.set(i.statut === 'VALIDE' ? 3 : 2);
    if (i.statut === 'VALIDE') { this.rapport.set(i); return; }
    this.api.toutes<ImportLigne>(`/api/imports/${i.id}/lignes`, { action: 'MISE_A_JOUR' }, 500, 20000).subscribe(l => {
      this.misesAJour.set(l);
      this.cochees.set(new Set(l.map(x => x.cle)));
    });
    this.api.toutes<ImportLigne>(`/api/imports/${i.id}/lignes`, { action: 'REJET' }, 500, 20000).subscribe(l => this.rejets.set(l));
  }

  private nettoyee(): Record<string, string> {
    return Object.fromEntries(Object.entries(this.correspondance).filter(([, v]) => !!v));
  }

  protected readonly Object = Object;
}
