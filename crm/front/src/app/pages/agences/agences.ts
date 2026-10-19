import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { AgenceGestion } from '../../core/modeles';
import { JourPipe } from '../../core/format';

interface Edition { code: string; codeImport: string; libelle: string; }

/** Referentiel des agences : cree, renomme ou archive par la Direction Generale ; jamais supprime. */
@Component({
  selector: 'app-agences',
  imports: [FormsModule, DialogModule, JourPipe],
  templateUrl: './agences.html'
})
export class Agences implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  /** Affichee dans les Parametres, sous Referentiels. */
  readonly integre = input(false);

  protected readonly agences = signal<AgenceGestion[] | null>(null);
  protected readonly archives = signal(false);
  protected readonly ouvert = signal(false);
  protected readonly cible = signal<AgenceGestion | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  edition: Edition = { code: '', codeImport: '', libelle: '' };

  ngOnInit(): void {
    this.charger();
  }

  basculerArchives(): void {
    this.archives.set(!this.archives());
    this.charger();
  }

  nouvelle(): void {
    this.cible.set(null);
    this.edition = { code: '', codeImport: '', libelle: '' };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  modifier(a: AgenceGestion): void {
    this.cible.set(a);
    this.edition = { code: a.code, codeImport: a.codeImport ?? '', libelle: a.libelle };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  enregistrer(): void {
    const corps = { code: this.edition.code.trim(), codeImport: this.edition.codeImport.trim() || null, libelle: this.edition.libelle.trim() };
    const a = this.cible();
    this.enCours.set(true);
    this.erreur.set(null);
    (a ? this.api.putSilencieux<AgenceGestion>(`/api/agences/${a.id}`, corps)
       : this.api.postSilencieux<AgenceGestion>('/api/agences', corps)).subscribe({
      next: r => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Agence enregistrée', detail: `${r.libelle} · rechargez la page pour la voir dans les listes de choix.` });
        this.charger();
      },
      error: e => { this.enCours.set(false); this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.'); }
    });
  }

  changerStatut(a: AgenceGestion, action: 'archivage' | 'reactivation'): void {
    this.api.post<AgenceGestion>(`/api/agences/${a.id}/${action}`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: action === 'archivage' ? 'Agence archivée' : 'Agence réactivée', detail: r.libelle });
      this.charger();
    });
  }

  private charger(): void {
    this.api.get<AgenceGestion[]>('/api/agences/gestion', { archives: this.archives() }).subscribe(a => this.agences.set(a));
  }
}
