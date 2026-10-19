import { Component, OnInit, inject, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Api } from '../../core/api';
import { FournisseurSms, ResultatAppelSms } from '../../core/modeles';
import { JourPipe } from '../../core/format';

type Edition = Omit<FournisseurSms, 'id' | 'parDefaut' | 'cleRenseignee' | 'urlAccuse' | 'modifiePar' | 'modifieLe'> & { cleApi: string };

const VIDE: Edition = {
  code: '', libelle: '', actif: true, expediteur: 'UBIPHARM', cleApi: '', formatNumero: 'INTERNATIONAL', urlEnvoi: 'https://', methodeEnvoi: 'POST',
  typeContenu: 'application/json', entetes: 'Authorization: Bearer {cle}', corps: '{"from": "{expediteur}", "to": "{numero}", "content": "{message}"}',
  cheminIdentifiant: 'id', cheminNbSms: '', cheminErreur: 'message', urlStatut: '', cheminStatut: '', champIdentifiantAccuse: 'id',
  champStatutAccuse: 'status', correspondanceStatuts: 'SENT=ENVOYE; DELIVERED=DISTRIBUE; FAILED=ECHEC', urlSolde: '', cheminSolde: ''
};

/** Fournisseurs SMS : chacun est decrit par son API HTTP, un nouveau se parametre ici sans developpement. */
@Component({
  selector: 'app-fournisseurs-sms',
  imports: [TooltipModule, FormsModule, JourPipe],
  templateUrl: './fournisseurs-sms.html'
})
export class FournisseursSms implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);

  protected readonly fournisseurs = signal<FournisseurSms[]>([]);
  protected readonly choisi = signal<FournisseurSms | null>(null);
  protected readonly nouveau = signal(false);
  protected readonly avance = signal(false);
  protected readonly essai = signal<ResultatAppelSms | null>(null);
  protected readonly solde = signal<ResultatAppelSms | null>(null);
  protected readonly occupe = signal(false);
  edition: Edition = { ...VIDE };
  test = { numero: '', message: 'Essai UBIPHARM CRM : ce SMS confirme le paramétrage du fournisseur.' };

  ngOnInit(): void {
    this.charger();
  }

  choisir(f: FournisseurSms): void {
    this.choisi.set(f);
    this.nouveau.set(false);
    this.essai.set(null);
    this.solde.set(null);
    const { id, parDefaut, cleRenseignee, urlAccuse, modifiePar, modifieLe, ...reste } = f;
    this.edition = { ...reste, cleApi: '' };
  }

  creer(modele: FournisseurSms | null = null): void {
    this.choisi.set(null);
    this.nouveau.set(true);
    this.essai.set(null);
    this.solde.set(null);
    this.avance.set(true);
    if (modele) {
      const { id, parDefaut, cleRenseignee, urlAccuse, modifiePar, modifieLe, ...reste } = modele;
      this.edition = { ...reste, code: '', libelle: modele.libelle + ' (copie)', cleApi: '' };
    } else {
      this.edition = { ...VIDE };
    }
  }

  enregistrer(): void {
    const f = this.choisi();
    const requete = f
      ? this.api.put<FournisseurSms>(`/api/sms/fournisseurs/${f.id}`, this.edition)
      : this.api.post<FournisseurSms>('/api/sms/fournisseurs', this.edition);
    requete.subscribe(r => {
      this.messages.add({ severity: 'success', summary: 'Fournisseur enregistré', detail: r.libelle });
      this.charger(r.id);
    });
  }

  parDefaut(): void {
    const f = this.choisi();
    if (!f) return;
    this.api.post<FournisseurSms>(`/api/sms/fournisseurs/${f.id}/defaut`).subscribe(r => {
      this.messages.add({ severity: 'success', summary: 'Fournisseur par défaut', detail: `Les relances SMS partent désormais par ${r.libelle}.` });
      this.charger(r.id);
    });
  }

  envoyerEssai(): void {
    const f = this.choisi();
    if (!f) return;
    this.occupe.set(true);
    this.api.post<ResultatAppelSms>(`/api/sms/fournisseurs/${f.id}/essai`, this.test).subscribe({
      next: r => { this.essai.set(r); this.occupe.set(false); },
      error: () => this.occupe.set(false)
    });
  }

  consulterSolde(): void {
    const f = this.choisi();
    if (!f) return;
    this.api.get<ResultatAppelSms>(`/api/sms/fournisseurs/${f.id}/solde`).subscribe(r => this.solde.set(r));
  }

  copier(texte: string): void {
    navigator.clipboard?.writeText(texte).then(() => this.messages.add({ severity: 'success', summary: 'Adresse copiée' }));
  }

  private charger(choisirId?: number): void {
    this.api.get<FournisseurSms[]>('/api/sms/fournisseurs').subscribe(liste => {
      this.fournisseurs.set(liste);
      const cible = liste.find(f => f.id === (choisirId ?? this.choisi()?.id)) ?? liste[0];
      if (cible) this.choisir(cible);
    });
  }
}
