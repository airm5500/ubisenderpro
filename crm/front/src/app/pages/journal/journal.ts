import { Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Page } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { JournalLigne, JournalReferentiel, auteur, jourIso } from './journal-modeles';

type Raccourci = 'jour' | '7' | '30' | null;

/** Qui a fait quoi : creations, modifications, desactivations, reactivations, connexions et envois de toute l'application. */
@Component({
  selector: 'app-journal',
  imports: [FormsModule, RouterLink, JourPipe],
  templateUrl: './journal.html',
  styleUrl: './journal.css',
  // Styles prefixes par .journal-page ou body.impression-journal : l'impression doit masquer le menu, hors du composant
  encapsulation: ViewEncapsulation.None
})
export class Journal implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  protected readonly auteur = auteur;

  criteres = { du: jourIso(), au: jourIso(), utilisateurId: '', type: '', nature: '', q: '' };
  /** Fiche client d'ou l'on vient (lien « Tout le journal »). */
  protected readonly client = signal<{ id: string; nom: string } | null>(null);
  protected readonly raccourci = signal<Raccourci>('jour');
  protected readonly referentiel = signal<JournalReferentiel | null>(null);
  protected readonly page = signal<Page<JournalLigne> | null>(null);
  protected readonly ouvert = signal<string | null>(null);
  protected readonly impression = signal<JournalLigne[] | null>(null);
  protected readonly imprimeLe = signal('');
  private numeroPage = 0;
  private readonly taille = 50;
  protected readonly nbPages = computed(() => Math.max(1, Math.ceil((this.page()?.total ?? 0) / this.taille)));

  ngOnInit(): void {
    const parametres = this.route.snapshot.queryParamMap;
    if (parametres.get('clientId')) {
      this.client.set({ id: parametres.get('clientId')!, nom: parametres.get('client') ?? 'n° ' + parametres.get('clientId') });
      // Tout l'historique du client, pas seulement celui du jour
      this.criteres.du = '';
      this.criteres.au = '';
      this.raccourci.set(null);
    }
    this.api.get<JournalReferentiel>('/api/journal/referentiel').subscribe(r => this.referentiel.set(r));
    this.rechercher();
  }

  periode(r: Exclude<Raccourci, null>): void {
    this.raccourci.set(r);
    this.criteres.au = jourIso();
    this.criteres.du = r === 'jour' ? jourIso() : jourIso(r === '7' ? -6 : -29);
    this.rechercher();
  }

  /** Une date saisie a la main n'est plus un raccourci. */
  datesSaisies(): void {
    this.raccourci.set(null);
  }

  oublierClient(): void {
    this.client.set(null);
    this.rechercher();
  }

  effacer(): void {
    this.criteres = { du: jourIso(), au: jourIso(), utilisateurId: '', type: '', nature: '', q: '' };
    this.raccourci.set('jour');
    this.client.set(null);
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

  basculer(l: JournalLigne): void {
    this.ouvert.set(this.ouvert() === l.cle ? null : l.cle);
  }

  exporter(format: 'csv' | 'xlsx'): void {
    const url = format === 'csv' ? '/api/journal/export' : '/api/journal/export-excel';
    this.api.telecharger(url + requete(this.filtres()), 'journal-actions.' + format);
  }

  /** Toutes les lignes des filtres (2000 au plus) dans une page imprimable, sans menu ni filtres. */
  imprimer(): void {
    this.api.toutes<JournalLigne>('/api/journal', this.filtres(), 200, 2000).subscribe(lignes => {
      this.imprimeLe.set(new Date().toLocaleString('fr-FR'));
      this.impression.set(lignes);
      setTimeout(() => {
        document.body.classList.add('impression-journal');
        window.print();
        document.body.classList.remove('impression-journal');
      });
    });
  }

  /** Rappel des filtres en tete de l'impression. */
  protected resume(): string {
    const c = this.criteres;
    const r = this.referentiel();
    const parties: string[] = [];
    parties.push(c.du || c.au ? 'Période : ' + (c.du ? 'du ' + francais(c.du) : '') + (c.au ? ' au ' + francais(c.au) : '') : 'Toutes les dates');
    if (c.utilisateurId) parties.push('Utilisateur : ' + (r?.utilisateurs.find(u => u.code === c.utilisateurId)?.libelle ?? c.utilisateurId));
    if (c.type) parties.push('Type : ' + (r?.types.find(t => t.code === c.type)?.libelle ?? c.type));
    if (c.nature) parties.push('Nature : ' + (r?.natures.find(n => n.code === c.nature)?.libelle ?? c.nature));
    if (c.q) parties.push('Recherche : « ' + c.q + ' »');
    if (this.client()) parties.push('Client : ' + this.client()!.nom);
    return parties.join(' · ');
  }

  protected get numero(): number { return this.numeroPage; }

  private filtres(): Record<string, string | null> {
    const f: Record<string, string | null> = Object.fromEntries(Object.entries(this.criteres).map(([k, v]) => [k, v || null]));
    f['clientId'] = this.client()?.id ?? null;
    return f;
  }

  private charger(): void {
    this.api.get<Page<JournalLigne>>('/api/journal', { ...this.filtres(), page: this.numeroPage, taille: this.taille })
      .subscribe(p => this.page.set(p));
  }
}

function francais(iso: string): string {
  const [a, m, j] = iso.split('-');
  return `${j}/${m}/${a}`;
}

function requete(filtres: Record<string, string | null>): string {
  const p = new URLSearchParams();
  Object.entries(filtres).forEach(([k, v]) => v && p.set(k, v));
  const texte = p.toString();
  return texte ? '?' + texte : '';
}
