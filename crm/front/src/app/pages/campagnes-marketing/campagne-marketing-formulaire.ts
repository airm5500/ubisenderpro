import { Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { CampagneMarketing, Evenement, Information, ListeDiffusion, ModeleMessage, Page, PieceJointe, Produit, Promotion } from '../../core/modeles';
import { JourPipe } from '../../core/format';
import { LIBELLES } from '../../core/format';

interface Formulaire {
  nom: string; listeId: number | null; modeleId: number | null; evenementId: number | null; informationId: number | null; promotionIds: Set<number>; produitIds: Set<number>; dateExecution: string; recurrence: string; finRecurrence: string;
  afficheId: number | null; afficheNom: string; joindreProduits: boolean;
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
  /** Produits a annoncer, choisis dans le catalogue (nouvelle campagne). */
  readonly produitsInitiaux = input<number[]>([]);
  /** Information client a annoncer, choisie dans Informations clients (nouvelle campagne). */
  readonly informationInitiale = input<number | null>(null);
  readonly enregistre = output<CampagneMarketing>();

  protected readonly listes = signal<ListeDiffusion[]>([]);
  protected readonly modeles = signal<ModeleMessage[]>([]);
  protected readonly promotions = signal<Promotion[]>([]);
  protected readonly evenements = signal<Evenement[]>([]);
  protected readonly informations = signal<Information[]>([]);
  /** Produits de retour en stock, et ceux deja cites par la campagne. */
  protected readonly produits = signal<{ id: number; libelle: string; detail: string }[]>([]);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  formulaire: Formulaire = this.vide();
  protected readonly depot = signal(false);

  preparer(): void {
    const c = this.campagne();
    this.erreur.set(null);
    this.formulaire = c
      ? { nom: c.nom, listeId: c.listeId, modeleId: c.modeleId, evenementId: c.evenementId, informationId: c.informationId, promotionIds: new Set(c.promotionIds), produitIds: new Set(c.produitIds ?? []),
          dateExecution: c.dateExecution ? c.dateExecution.substring(0, 16) : '', recurrence: c.recurrence ?? '', finRecurrence: c.finRecurrence ?? '',
          afficheId: c.afficheId, afficheNom: c.afficheNom ?? '', joindreProduits: c.joindreProduits }
      : { ...this.vide(), produitIds: new Set(this.produitsInitiaux()) };
    this.api.get<ListeDiffusion[]>('/api/marketing/listes').subscribe(l => this.listes.set(l));
    const information = c ? null : this.informationInitiale();
    this.api.get<ModeleMessage[]>('/api/marketing/modeles').subscribe(m => {
      this.modeles.set(m);
      this.api.get<Information[]>('/api/marketing/informations').subscribe(i => {
        this.informations.set(i.filter(x => !x.expiree || x.id === this.formulaire.informationId));
        if (information) this.choisirInformation(information);
      });
    });
    this.api.get<Promotion[]>('/api/marketing/promotions', { statut: 'ACTIVE' }).subscribe(p => this.promotions.set(p));
    this.api.get<Evenement[]>('/api/marketing/evenements').subscribe(e => this.evenements.set(e.filter(x => x.prochaineDate || x.id === this.formulaire.evenementId)));
    const choisis = [...this.formulaire.produitIds];
    this.api.get<Page<Produit>>('/api/marketing/produits', { disponibilite: 'DISPONIBLE', page: 0, taille: 100 }).subscribe(p => {
      // Les produits de retour en premier ; un produit deja choisi reste visible meme s'il n'est plus « de retour »
      const visibles = p.elements.filter(x => x.deRetour || choisis.includes(x.id)).sort((a, b) => Number(b.deRetour) - Number(a.deRetour));
      this.produits.set(visibles.map(x => ({ id: x.id, libelle: `${x.code} · ${x.libelle}`,
        detail: x.deRetour && x.disponibiliteLe ? `de retour le ${x.disponibiliteLe.substring(8, 10)}/${x.disponibiliteLe.substring(5, 7)}` : (x.conditionnement ?? '') })));
    });
  }

  /** L'affiche part au serveur des qu'elle est choisie ; la campagne n'en garde que l'identifiant. */
  deposer(entree: HTMLInputElement): void {
    const fichier = entree.files?.[0];
    if (!fichier) return;
    this.depot.set(true);
    this.erreur.set(null);
    this.api.envoyerFichier<PieceJointe>('/api/marketing/pieces-jointes', fichier).subscribe({
      next: p => { this.depot.set(false); this.formulaire.afficheId = p.id; this.formulaire.afficheNom = p.nomFichier; entree.value = ''; },
      error: e => { this.depot.set(false); this.erreur.set(e?.error?.message ?? "Dépôt de l'affiche impossible."); entree.value = ''; }
    });
  }

  retirerAffiche(): void {
    this.formulaire.afficheId = null;
    this.formulaire.afficheNom = '';
  }

  private vide(): Formulaire {
    return { nom: '', listeId: null, modeleId: null, evenementId: null, informationId: null, promotionIds: new Set(), produitIds: new Set(), dateExecution: '', recurrence: '', finRecurrence: '',
      afficheId: null, afficheNom: '', joindreProduits: false };
  }

  /** L'evenement choisi propose sa date d'envoi (parametres du Marketing). */
  choisirEvenement(id: number | null): void {
    this.formulaire.evenementId = id;
    const e = this.evenements().find(x => x.id === id);
    if (e?.envoiPropose) this.formulaire.dateExecution = e.envoiPropose.substring(0, 16);
  }

  /** L'information choisie propose le modele prevu pour son type, son titre comme nom et un envoi a l'heure suivante. */
  choisirInformation(id: number | null): void {
    this.formulaire.informationId = id;
    const i = this.informations().find(x => x.id === id);
    if (!i) return;
    const actuel = this.modeles().find(m => m.id === this.formulaire.modeleId);
    if (!actuel || actuel.typeCampagne !== 'INFORMATION' || (actuel.typeInformation && actuel.typeInformation !== i.typeInformation)) {
      const prevu = this.modeles().find(m => m.typeCampagne === 'INFORMATION' && m.typeInformation === i.typeInformation)
        ?? this.modeles().find(m => m.typeCampagne === 'INFORMATION' && !m.typeInformation);
      if (prevu) this.formulaire.modeleId = prevu.id;
    }
    if (!this.formulaire.nom.trim()) this.formulaire.nom = i.titre;
    if (!this.formulaire.dateExecution) {
      const h = new Date();
      h.setMinutes(0, 0, 0);
      h.setHours(h.getHours() + 1);
      const z = (n: number) => String(n).padStart(2, '0');
      this.formulaire.dateExecution = `${h.getFullYear()}-${z(h.getMonth() + 1)}-${z(h.getDate())}T${z(h.getHours())}:00`;
    }
  }

  protected modeleInformation(): boolean {
    return this.modeles().find(m => m.id === this.formulaire.modeleId)?.typeCampagne === 'INFORMATION';
  }

  protected evenementChoisi(): Evenement | undefined {
    return this.evenements().find(x => x.id === this.formulaire.evenementId);
  }

  protected modeleEvenement(): boolean {
    return this.modeles().find(m => m.id === this.formulaire.modeleId)?.typeCampagne === 'EVENEMENT';
  }

  basculer(id: number, coche: boolean): void {
    if (coche) this.formulaire.promotionIds.add(id); else this.formulaire.promotionIds.delete(id);
  }

  basculerProduit(id: number, coche: boolean): void {
    if (coche) this.formulaire.produitIds.add(id); else this.formulaire.produitIds.delete(id);
  }

  enregistrer(): void {
    const c = this.campagne();
    const f = this.formulaire;
    const corps = { nom: f.nom.trim(), listeId: f.listeId, modeleId: f.modeleId, evenementId: f.evenementId, informationId: f.informationId, promotionIds: [...f.promotionIds], produitIds: [...f.produitIds], dateExecution: f.dateExecution ? f.dateExecution + ':00' : null,
      recurrence: f.recurrence || null, finRecurrence: f.recurrence && f.finRecurrence ? f.finRecurrence : null,
      afficheId: f.afficheId, joindreProduits: f.joindreProduits && (f.promotionIds.size > 0 || f.produitIds.size > 0) };
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
