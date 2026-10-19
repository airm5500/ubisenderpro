import { Component, OnInit, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Evenement } from '../../core/modeles';
import { JourPipe, LIBELLES } from '../../core/format';

interface Edition { nom: string; nature: string; dateDebut: string; dateFin: string; annuel: boolean; }

/** Calendrier des evenements annonces aux clients : fetes, jours feries, fermetures d'agence. */
@Component({
  selector: 'app-evenements',
  imports: [TooltipModule, FormsModule, DialogModule, JourPipe],
  templateUrl: './evenements.html'
})
export class Evenements implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly L = LIBELLES;
  protected readonly natures = Object.entries(LIBELLES.natureEvenement);

  protected readonly evenements = signal<Evenement[]>([]);
  protected readonly archives = signal(false);
  protected readonly ouvert = signal(false);
  protected readonly cible = signal<Evenement | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  edition: Edition = vide();

  ngOnInit(): void {
    this.charger();
  }

  basculerArchives(): void {
    this.archives.set(!this.archives());
    this.charger();
  }

  nouveau(): void {
    this.cible.set(null);
    this.edition = vide();
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  modifier(e: Evenement): void {
    this.cible.set(e);
    this.edition = { nom: e.nom, nature: e.nature, dateDebut: e.dateDebut, dateFin: e.dateFin ?? '', annuel: e.annuel };
    this.erreur.set(null);
    this.ouvert.set(true);
  }

  enregistrer(): void {
    const corps = { ...this.edition, nom: this.edition.nom.trim(), dateDebut: this.edition.dateDebut || null, dateFin: this.edition.dateFin || null };
    const e = this.cible();
    this.enCours.set(true);
    this.erreur.set(null);
    (e ? this.api.putSilencieux<Evenement>(`/api/marketing/evenements/${e.id}`, corps)
       : this.api.postSilencieux<Evenement>('/api/marketing/evenements', corps)).subscribe({
      next: r => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: 'Événement enregistré', detail: r.nom });
        this.charger();
      },
      error: x => {
        this.enCours.set(false);
        this.erreur.set(x?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  changerStatut(e: Evenement, action: 'archivage' | 'reactivation'): void {
    this.api.post<Evenement>(`/api/marketing/evenements/${e.id}/${action}`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: action === 'archivage' ? 'Événement archivé' : 'Événement réactivé', detail: r.nom });
      this.charger();
    });
  }

  private charger(): void {
    this.api.get<Evenement[]>('/api/marketing/evenements', { archives: this.archives() }).subscribe(e => this.evenements.set(e));
  }
}

function vide(): Edition {
  return { nom: '', nature: 'FETE', dateDebut: '', dateFin: '', annuel: false };
}
