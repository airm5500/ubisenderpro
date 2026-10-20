import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { Parametre } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { Charte, CharteService } from '../../core/charte';
import { Auth } from '../../core/auth';
import { ReferentielsListe, TypeReferentiel } from '../referentiels/referentiels';
import { Agences } from '../agences/agences';
import { FournisseursSms } from '../fournisseurs-sms/fournisseurs-sms';

const CATEGORIES: [string, string, string][] = [
  ['GENERAL', 'Général', "Nom de l'application, adresse publique HTTPS du CRM et coordonnées de l'entreprise citées dans les messages."],
  ['CHARTE', 'Charte', "Logo, icône de l'onglet du navigateur et couleurs de l'application."],
  ['ENVOI', "Mode d'envoi", "Canal et mode WhatsApp proposés d'office."],
  ['ARCHIVAGE', 'Archivage', 'Copie des documents reçus et produits (imports, rejets, exports, affiches, listes de produits) sur le serveur.'],
  ['CAMPAGNE', 'Campagnes', 'Fenêtre par défaut des relances avant échéance.'],
  ['WHATSAPP', 'WhatsApp', 'Rythme des envois depuis les numéros des agents.'],
  ['EMAIL', 'E-mail', "Expéditeur et rythme des e-mails. Le serveur d'envoi (SMTP) se règle dans le fichier de configuration du serveur."],
  ['SMS', 'SMS', 'Rythme des envois ; le fournisseur se règle dans Fournisseurs SMS.'],
  ['SECURITE', 'Sécurité', 'Mots de passe et verrouillage des comptes.'],
  ['IMPORT', 'Imports', 'Taille des fichiers importés.'],
  ['MARKETING', 'Marketing', 'Désabonnement des clients par une réponse WhatsApp.'],
  ['SUPPORT', 'Centre de support', "Conservation et rythme de capture des erreurs du diagnostic, seuil d'alerte du disque d'archivage."],
  ['FOURNISSEURS_SMS', 'Fournisseurs SMS', '']
];

/** Regroupement des categories dans la liste de gauche, comme les referentiels. */
const GROUPES: [string, string[]][] = [
  ['Entreprise', ['GENERAL', 'CHARTE', 'ARCHIVAGE']],
  ['Envois', ['ENVOI', 'CAMPAGNE', 'WHATSAPP', 'EMAIL', 'SMS', 'FOURNISSEURS_SMS', 'MARKETING']],
  ['Sécurité et système', ['SECURITE', 'IMPORT', 'SUPPORT']]
];

/** Listes de reference tenues dans les Parametres (droit Referentiels). */
const REFERENTIELS: [string, string, string][] = [
  ['AGENCES', 'Agences', ''],
  ['REGION', 'Régions', 'Régions et districts autonomes.'],
  ['VILLE', 'Villes', 'Villes et leur région.'],
  ['COMMUNE', 'Communes', 'Communes et leur ville.'],
  ['TOURNEE', 'Tournées', 'Tournées de livraison et leur agence.'],
  ['SEGMENT', 'Segments', 'Segments de clients et leur badge.']
];

/** Parametres de l'application, par categorie ; seuls ceux qui ont change sont envoyes. */
@Component({
  selector: 'app-parametres',
  imports: [FormsModule, JourPipe, ReferentielsListe, Agences, FournisseursSms],
  templateUrl: './parametres.html'
})
export class Parametres implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly charteService = inject(CharteService);
  protected readonly auth = inject(Auth);
  protected readonly referentiels = REFERENTIELS;

  protected readonly parametres = signal<Parametre[]>([]);
  protected readonly categorie = signal('GENERAL');
  protected saisies: Record<string, string | number | null> = {};

  protected readonly categories = computed(() => CATEGORIES.filter(([code]) => this.disponible(code)));
  protected readonly groupes = computed(() => GROUPES
    .map(([titre, codes]) => [titre, CATEGORIES.filter(([code]) => codes.includes(code) && this.disponible(code))] as [string, [string, string, string][]])
    .filter(([, liste]) => liste.length));
  protected readonly courante = computed(() => [...CATEGORIES, ...REFERENTIELS].find(([code]) => code === this.categorie()));
  protected readonly typeReferentiel = computed(() => this.categorie() as TypeReferentiel);
  protected readonly estReferentiel = computed(() => REFERENTIELS.some(([code]) => code === this.categorie()));
  protected readonly liste = computed(() => this.parametres().filter(p => p.categorie === this.categorie()));

  ngOnInit(): void {
    if (this.auth.peut('PARAMETRES')) {
      this.api.get<Parametre[]>('/api/parametres').subscribe(p => this.remplir(p));
    } else {
      this.categorie.set(this.auth.peut('FOURNISSEURS_SMS') ? 'FOURNISSEURS_SMS' : this.auth.peut('AGENCES') ? 'AGENCES' : 'REGION');
    }
  }

  /** Categorie montree : la charte toujours (avec le droit Parametres), les fournisseurs SMS avec leur droit, les autres si elles ont des parametres. */
  private disponible(code: string): boolean {
    if (code === 'FOURNISSEURS_SMS') return this.auth.peut('FOURNISSEURS_SMS');
    if (!this.auth.peut('PARAMETRES')) return false;
    return code === 'CHARTE' || this.parametres().some(p => p.categorie === code);
  }

  /** Choix d'un parametre CHOIX, ecrits CODE=Libelle|CODE=Libelle. */
  options(p: Parametre): [string, string][] {
    return (p.options ?? '').split('|').filter(o => o).map(o => {
      const [code, libelle] = o.split('=');
      return [code, libelle ?? code];
    });
  }

  deposer(code: 'logo' | 'icone' | 'banniere', evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    this.api.envoyerFichier<Charte>(`/api/charte/${code}`, fichier).subscribe(c => {
      this.charteService.appliquer(c);
      this.messages.add({ severity: 'success', summary: code === 'logo' ? 'Logo remplacé' : code === 'banniere' ? 'Bannière enregistrée' : 'Icône remplacée' });
    });
  }

  reinitialiser(code: 'logo' | 'icone' | 'banniere'): void {
    this.api.post<Charte>(`/api/charte/${code}/reinitialisation`).subscribe(c => {
      this.charteService.appliquer(c);
      this.messages.add({ severity: 'success', summary: code === 'logo' ? "Logo d'origine rétabli" : code === 'banniere' ? 'Bannière retirée' : "Icône d'origine rétablie" });
    });
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
    if (this.parametres().length) {
      // Couleurs, nom et mode par defaut s'appliquent sans recharger la page
      this.charteService.charger();
    }
    this.parametres.set(liste);
    this.saisies = Object.fromEntries(liste.map(p => [p.cle, p.valeur ?? '']));
  }
}
