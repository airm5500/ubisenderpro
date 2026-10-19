import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { CampagneMarketing, ListeDiffusion, ModeleMessage } from '../../core/modeles';
import { LIBELLES } from '../../core/format';

interface Formulaire { nom: string; listeId: number | null; modeleId: number | null; dateExecution: string; }

/** Creation ou modification d'une campagne promotionnelle : une liste, un modele, une date. */
@Component({
  selector: 'app-campagne-marketing-formulaire',
  imports: [FormsModule, DialogModule],
  templateUrl: './campagne-marketing-formulaire.html'
})
export class CampagneMarketingFormulaire {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly L = LIBELLES;

  readonly ouvert = model(false);
  readonly campagne = input<CampagneMarketing | null>(null);
  readonly enregistre = output<CampagneMarketing>();

  protected readonly listes = signal<ListeDiffusion[]>([]);
  protected readonly modeles = signal<ModeleMessage[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = { nom: '', listeId: null, modeleId: null, dateExecution: '' };

  preparer(): void {
    const c = this.campagne();
    this.erreur.set(null);
    this.formulaire = c
      ? { nom: c.nom, listeId: c.listeId, modeleId: c.modeleId, dateExecution: c.dateExecution ? c.dateExecution.substring(0, 16) : '' }
      : { nom: '', listeId: null, modeleId: null, dateExecution: '' };
    this.api.get<ListeDiffusion[]>('/api/marketing/listes').subscribe(l => this.listes.set(l));
    this.api.get<ModeleMessage[]>('/api/marketing/modeles').subscribe(m => this.modeles.set(m));
  }

  enregistrer(): void {
    const c = this.campagne();
    const f = this.formulaire;
    const corps = { nom: f.nom.trim(), listeId: f.listeId, modeleId: f.modeleId, dateExecution: f.dateExecution ? f.dateExecution + ':00' : null };
    this.enCours.set(true);
    this.erreur.set(null);
    const requete = c ? this.api.putSilencieux<CampagneMarketing>(`/api/marketing/campagnes/${c.id}`, corps)
      : this.api.postSilencieux<CampagneMarketing>('/api/marketing/campagnes', corps);
    requete.subscribe({
      next: campagne => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.messages.add({ severity: 'success', summary: c ? 'Campagne enregistrée' : 'Campagne créée', detail: campagne.nom });
        this.enregistre.emit(campagne);
      },
      error: e => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
