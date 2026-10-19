import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { CampagneMarketing, ListeDiffusion, ModeleMessage, Promotion } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { LIBELLES } from '../../core/format';

interface Formulaire {
  nom: string; listeId: number | null; modeleId: number | null; promotionIds: Set<number>; dateExecution: string; recurrence: string; finRecurrence: string;
}

/** Creation ou modification d'une campagne promotionnelle : une liste, un modele, des promotions actives, une date. */
@Component({
  selector: 'app-campagne-marketing-formulaire',
  imports: [FormsModule, DialogModule, JourPipe],
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
  protected readonly promotions = signal<Promotion[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = { nom: '', listeId: null, modeleId: null, promotionIds: new Set(), dateExecution: '', recurrence: '', finRecurrence: '' };

  preparer(): void {
    const c = this.campagne();
    this.erreur.set(null);
    this.formulaire = c
      ? { nom: c.nom, listeId: c.listeId, modeleId: c.modeleId, promotionIds: new Set(c.promotionIds),
          dateExecution: c.dateExecution ? c.dateExecution.substring(0, 16) : '', recurrence: c.recurrence ?? '', finRecurrence: c.finRecurrence ?? '' }
      : { nom: '', listeId: null, modeleId: null, promotionIds: new Set(), dateExecution: '', recurrence: '', finRecurrence: '' };
    this.api.get<ListeDiffusion[]>('/api/marketing/listes').subscribe(l => this.listes.set(l));
    this.api.get<ModeleMessage[]>('/api/marketing/modeles').subscribe(m => this.modeles.set(m));
    this.api.get<Promotion[]>('/api/marketing/promotions', { statut: 'ACTIVE' }).subscribe(p => this.promotions.set(p));
  }

  basculer(id: number, coche: boolean): void {
    if (coche) this.formulaire.promotionIds.add(id); else this.formulaire.promotionIds.delete(id);
  }

  enregistrer(): void {
    const c = this.campagne();
    const f = this.formulaire;
    const corps = { nom: f.nom.trim(), listeId: f.listeId, modeleId: f.modeleId, promotionIds: [...f.promotionIds], dateExecution: f.dateExecution ? f.dateExecution + ':00' : null,
      recurrence: f.recurrence || null, finRecurrence: f.recurrence && f.finRecurrence ? f.finRecurrence : null };
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
