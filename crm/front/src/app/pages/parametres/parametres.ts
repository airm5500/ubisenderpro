import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Parametre } from '../../core/modeles';
import { JourPipe } from '../../core/format';

const CATEGORIES: [string, string, string][] = [
  ['GENERAL', 'Général', 'Adresse publique du CRM.'],
  ['CAMPAGNE', 'Campagnes', 'Fenêtre par défaut des relances avant échéance.'],
  ['WHATSAPP', 'WhatsApp', 'Rythme des envois depuis les numéros des agents.'],
  ['EMAIL', 'E-mail', "Expéditeur et rythme des e-mails. Le serveur d'envoi (SMTP) se règle dans le fichier de configuration du serveur."],
  ['SMS', 'SMS', 'Rythme des envois ; le fournisseur se règle dans Fournisseurs SMS.'],
  ['SECURITE', 'Sécurité', 'Mots de passe et verrouillage des comptes.'],
  ['IMPORT', 'Imports', 'Taille des fichiers importés.'],
  ['MARKETING', 'Marketing', 'Désabonnement des clients par une réponse WhatsApp.']
];

/** Parametres de l'application, par categorie ; seuls ceux qui ont change sont envoyes. */
@Component({
  selector: 'app-parametres',
  imports: [FormsModule, JourPipe],
  templateUrl: './parametres.html'
})
export class Parametres implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  protected readonly parametres = signal<Parametre[]>([]);
  protected readonly categorie = signal('GENERAL');
  protected saisies: Record<string, string | number | null> = {};

  protected readonly categories = computed(() => CATEGORIES.filter(([code]) => this.parametres().some(p => p.categorie === code)));
  protected readonly courante = computed(() => CATEGORIES.find(([code]) => code === this.categorie()));
  protected readonly liste = computed(() => this.parametres().filter(p => p.categorie === this.categorie()));

  ngOnInit(): void {
    this.api.get<Parametre[]>('/api/parametres').subscribe(p => this.remplir(p));
  }

  modifies(): number {
    return this.liste().filter(p => this.change(p)).length;
  }

  /** Un champ numerique rend un nombre : on compare les textes. */
  private texte(cle: string): string {
    const v = this.saisies[cle];
    return v === null || v === undefined ? '' : String(v).trim();
  }

  private change(p: Parametre): boolean {
    return this.texte(p.cle) !== (p.valeur ?? '').trim();
  }

  annuler(): void {
    this.remplir(this.parametres());
  }

  enregistrer(): void {
    const changes = this.liste()
      .filter(p => this.change(p))
      .map(p => ({ cle: p.cle, valeur: this.texte(p.cle) }));
    this.api.put<Parametre[]>('/api/parametres', changes).subscribe(p => {
      this.remplir(p);
      this.messages.add({ severity: 'success', summary: 'Paramètres enregistrés', detail: `${changes.length} valeur${changes.length > 1 ? 's' : ''} modifiée${changes.length > 1 ? 's' : ''}.` });
    });
  }

  private remplir(liste: Parametre[]): void {
    this.parametres.set(liste);
    this.saisies = Object.fromEntries(liste.map(p => [p.cle, p.valeur ?? '']));
  }
}
