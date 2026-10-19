import { Component, ElementRef, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { EtatSms, ModeleMessage, SourceVariable, Variable } from '../../core/modeles';
import { JourPipe, LIBELLES, compterSms } from '../../core/format';

interface Morceau { texte: string; variable: boolean; inconnue: boolean; }

const VARIABLE = /\{([a-z][a-z0-9_]*)\}/g;

@Component({
  selector: 'app-modeles',
  imports: [FormsModule, DialogModule, JourPipe],
  templateUrl: './modeles.html'
})
export class Modeles implements OnInit {
  private readonly api = inject(Api);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(Auth);
  protected readonly L = LIBELLES;

  /** Donnee de route : les modeles des relances ou ceux des promotions du Marketing. */
  readonly module = input<'RECOUVREMENT' | 'MARKETING'>('RECOUVREMENT');
  protected readonly marketing = computed(() => this.module() === 'MARKETING');
  protected readonly types = computed(() => this.marketing() ? [['PROMOTION', 'Promotion'], ['EVENEMENT', 'Événement (fête, férié, fermeture)']]
    : Object.entries(LIBELLES.typeCampagne).filter(([code]) => code !== 'PROMOTION' && code !== 'EVENEMENT'));
  /** Ecrire les modeles ; les variables ne s'administrent que cote Recouvrement. */
  protected readonly peutGerer = computed(() => this.auth.peut(this.marketing() ? 'MODELES_MARKETING' : 'MODELES_GERER'));
  protected readonly peutVariables = computed(() => !this.marketing() && this.auth.peut('MODELES_GERER'));
  private readonly base = computed(() => this.marketing() ? '/api/marketing/modeles' : '/api/recouvrement/modeles-messages');
  private readonly zone = viewChild<ElementRef<HTMLTextAreaElement>>('zone');

  protected readonly vue = signal<'modeles' | 'variables'>('modeles');
  protected readonly archives = signal(false);
  protected readonly modeles = signal<ModeleMessage[]>([]);
  protected readonly variables = signal<Variable[]>([]);
  protected readonly sources = signal<SourceVariable[]>([]);
  protected readonly choisi = signal<ModeleMessage | null>(null);
  protected readonly enEdition = signal(false);
  edition = { nom: '', typeCampagne: 'APRES_ECHEANCE', canal: 'WHATSAPP', objet: '', contenu: '', metaModele: '', metaLangue: 'fr', metaEntete: 'AUCUN' };
  protected readonly contenu = signal('');
  /** Variables qu'un modele de relance peut citer : ni promotion, ni produit disponible, ni evenement (Marketing). */
  protected readonly insertables = computed(() => this.marketing() ? this.variables()
    : this.variables().filter(v => !/^calcul\.(promotion_|produits_|evenement_)/.test(v.source)));
  /** Parametres {{1}}, {{2}}... du modele Meta : les variables du message, dans leur ordre d'apparition. */
  protected readonly ordreMeta = computed(() => [...new Set([...this.contenu().matchAll(/\{([a-z][a-z0-9_]*)\}/g)].map(m => m[1]))]
    .map((v, i) => `{{${i + 1}}} = ${v}`));
  protected readonly objet = signal('');
  /** Canal du modele affiche ou edite : l'apercu change de forme (bulle WhatsApp ou e-mail). */
  protected readonly canalApercu = signal('WHATSAPP');

  protected readonly fenetreVariable = signal<Variable | 'nouvelle' | null>(null);
  variable = { code: '', libelle: '', source: '', exemple: '' };

  protected readonly parType = computed(() => this.types()
    .map(([code, libelle]) => ({ code, libelle, modeles: this.modeles().filter(m => m.typeCampagne === code) }))
    .filter(g => g.modeles.length));

  /** Apercu : variables remplacees par leur exemple, surlignees ; une variable inconnue est signalee. */
  protected readonly apercu = computed<Morceau[]>(() => this.morceaux(this.enEdition() ? this.contenu() : this.choisi()?.contenu ?? ''));
  protected readonly apercuObjet = computed<Morceau[]>(() => this.morceaux(this.enEdition() ? this.objet() : this.choisi()?.objet ?? ''));

  /** Longueur estimee avec les valeurs d'exemple : les vraies valeurs peuvent l'allonger. */
  protected readonly comptageSms = computed(() => compterSms(this.apercu().map(p => p.texte).join('')));
  protected readonly etatSms = signal<EtatSms | null>(null);
  protected readonly expediteurSms = computed(() => this.etatSms()?.expediteur || 'UBIPHARM');

  protected readonly apercuInconnues = computed(() => [...this.apercu(), ...this.apercuObjet()].some(m => m.inconnue));

  private morceaux(texte: string): Morceau[] {
    const exemples = new Map(this.variables().map(v => [v.code, v.exemple || v.libelle]));
    const morceaux: Morceau[] = [];
    let dernier = 0;
    for (const m of texte.matchAll(VARIABLE)) {
      morceaux.push({ texte: texte.substring(dernier, m.index), variable: false, inconnue: false });
      const connu = exemples.has(m[1]);
      morceaux.push({ texte: connu ? exemples.get(m[1])! : m[0], variable: true, inconnue: !connu });
      dernier = m.index! + m[0].length;
    }
    morceaux.push({ texte: texte.substring(dernier), variable: false, inconnue: false });
    return morceaux;
  }

  ngOnInit(): void {
    this.chargerModeles();
    this.chargerVariables();
    this.api.get<EtatSms>('/api/sms/etat').subscribe(e => this.etatSms.set(e));
    if (this.peutVariables()) this.api.get<SourceVariable[]>('/api/recouvrement/variables/sources').subscribe(s => this.sources.set(s));
  }

  basculerArchives(): void {
    this.archives.set(!this.archives());
    this.choisi.set(null);
    this.enEdition.set(false);
    this.chargerModeles();
  }

  choisir(m: ModeleMessage): void {
    this.choisi.set(m);
    this.canalApercu.set(m.canal);
    this.enEdition.set(false);
  }

  nouveau(): void {
    this.choisi.set(null);
    this.edition = { nom: '', typeCampagne: this.marketing() ? 'PROMOTION' : 'APRES_ECHEANCE', canal: 'WHATSAPP', objet: '', contenu: '',
      metaModele: '', metaLangue: 'fr', metaEntete: 'AUCUN' };
    this.contenu.set('');
    this.objet.set('');
    this.canalApercu.set('WHATSAPP');
    this.enEdition.set(true);
  }

  modifier(m: ModeleMessage): void {
    this.edition = { nom: m.nom, typeCampagne: m.typeCampagne, canal: m.canal, objet: m.objet ?? '', contenu: m.contenu,
      metaModele: m.metaModele ?? '', metaLangue: m.metaLangue ?? 'fr', metaEntete: m.metaEntete ?? 'AUCUN' };
    this.contenu.set(m.contenu);
    this.objet.set(m.objet ?? '');
    this.canalApercu.set(m.canal);
    this.enEdition.set(true);
  }

  /** Insere {code} a la position du curseur. */
  inserer(code: string): void {
    const zone = this.zone()?.nativeElement;
    const texte = this.edition.contenu;
    const debut = zone?.selectionStart ?? texte.length;
    const fin = zone?.selectionEnd ?? texte.length;
    this.edition.contenu = texte.substring(0, debut) + `{${code}}` + texte.substring(fin);
    this.contenu.set(this.edition.contenu);
    setTimeout(() => { zone?.focus(); zone?.setSelectionRange(debut + code.length + 2, debut + code.length + 2); });
  }

  /** Champs Meta vides : envoyes absents, pour ne pas etre refuses par les controles de format. */
  private corps(): object {
    const e = this.edition;
    return { ...e, metaModele: e.metaModele.trim() || null, metaLangue: e.metaLangue.trim() || null };
  }

  enregistrer(): void {
    const m = this.choisi();
    const requete = m
      ? this.api.put<ModeleMessage>(`${this.base()}/${m.id}`, this.corps())
      : this.api.post<ModeleMessage>(this.base(), this.corps());
    requete.subscribe(enregistre => {
      this.messages.add({ severity: 'success', summary: 'Modèle enregistré', detail: enregistre.nom });
      this.enEdition.set(false);
      this.chargerModeles(enregistre.id);
    });
  }

  archiver(m: ModeleMessage): void {
    this.api.post(`${this.base()}/${m.id}/archivage`).subscribe(() => {
      this.messages.add({ severity: 'info', summary: 'Modèle archivé', detail: `${m.nom} n'est plus proposé dans les nouvelles campagnes.` });
      this.choisi.set(null);
      this.chargerModeles();
    });
  }

  ouvrirVariable(v: Variable | 'nouvelle'): void {
    this.variable = v === 'nouvelle' ? { code: '', libelle: '', source: '', exemple: '' } : { code: v.code, libelle: v.libelle, source: v.source, exemple: v.exemple };
    this.fenetreVariable.set(v);
  }

  enregistrerVariable(): void {
    const v = this.fenetreVariable();
    const requete = v && v !== 'nouvelle'
      ? this.api.put<Variable>(`/api/recouvrement/variables/${v.id}`, this.variable)
      : this.api.post<Variable>('/api/recouvrement/variables', this.variable);
    requete.subscribe(() => { this.fenetreVariable.set(null); this.chargerVariables(); });
  }

  archiverVariable(v: Variable): void {
    this.api.post(`/api/recouvrement/variables/${v.id}/archivage`).subscribe(() => this.chargerVariables());
  }

  libelleSource(source: string): string {
    return this.sources().find(s => s.source === source)?.libelle ?? source;
  }

  private chargerModeles(choisirId?: number): void {
    this.api.get<ModeleMessage[]>(this.base(), { statut: this.archives() ? 'ARCHIVE' : 'ACTIF' }).subscribe(m => {
      this.modeles.set(m);
      const cible = m.find(x => x.id === (choisirId ?? this.choisi()?.id)) ?? (this.enEdition() ? null : m[0] ?? null);
      this.choisi.set(cible);
      if (cible && !this.enEdition()) this.canalApercu.set(cible.canal);
    });
  }

  private chargerVariables(): void {
    this.api.get<Variable[]>(this.marketing() ? '/api/marketing/variables' : '/api/recouvrement/variables').subscribe(v => this.variables.set(v));
  }
}
