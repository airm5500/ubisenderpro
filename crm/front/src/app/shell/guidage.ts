import { Component, DestroyRef, ElementRef, NgZone, OnInit, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { MessageService, ToastMessageOptions } from 'primeng/api';
import { filter } from 'rxjs';
import { AideService, decouperTexte } from '../core/aide';
import { AIDE_PAGES, TexteAide } from '../core/aide-textes';
import { Auth } from '../core/auth';

/** Elements qui recoivent une bulle : boutons et liens presentes comme des boutons. */
const BOUTONS = 'button, a.ba, a.bp, a.bs, a.bd, a.bi, label.bs, label.bp, [role="button"]';
/** Bouton qui valide une saisie : ses champs obligatoires vides sont signales. */
const VALIDATION = '.bp, button[type="submit"]';
const DELAI_BULLE_MS = 350;
const DUREE_COCHE_MS = 1600;

interface Bulle { titre: string; texte: string; grise: string | null; haut: number; gauche: number; dessous: boolean; }

/**
 * Aide et guidage (maquette validee le 20/10/2026) :
 * 1B bulle detaillee au survol de chaque bouton, 2B « ? » a cote du titre de chaque ecran qui ouvre le panneau d'aide,
 * 3C retours animes pendant la saisie (champ refuse qui tremble avec son message, coche de reussite pres du bouton).
 * Tout passe par des ecouteurs globaux : les ecrans n'ont rien a declarer.
 */
@Component({
  selector: 'app-guidage',
  imports: [FormsModule],
  templateUrl: './guidage.html',
  styleUrl: './guidage.css'
})
export class Guidage implements OnInit {
  protected readonly aide = inject(AideService);
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly zone = inject(NgZone);
  private readonly hote = inject(ElementRef<HTMLElement>);
  private readonly detruit = inject(DestroyRef);

  /** Chemin de la route affichee (« campagnes/:id ») : cle des textes de l'ecran. */
  protected readonly ecran = signal('');
  protected readonly bulle = signal<Bulle | null>(null);
  protected readonly panneau = signal(false);
  protected readonly edition = signal(false);
  protected readonly coche = signal<{ haut: number; gauche: number } | null>(null);
  protected titreSaisi = '';
  protected texteSaisi = '';

  protected readonly page = computed<TexteAide>(() => this.aide.texte('PAGE', this.ecran())
    ?? { titre: document.querySelector('main h1')?.textContent?.trim() || 'Aide', texte: "Pas encore d'aide écrite pour cet écran." });
  protected readonly blocs = computed(() => decouperTexte(this.page().texte));
  protected readonly pageReecrite = computed(() => !!this.aide.reecrit('PAGE', this.ecran()));
  protected readonly pageConnue = computed(() => !!AIDE_PAGES[this.ecran()] || this.pageReecrite());

  private cible: HTMLElement | null = null;
  private minuterie: ReturnType<typeof setTimeout> | undefined;
  private minuterieCoche: ReturnType<typeof setTimeout> | undefined;
  private dernierClic: { el: HTMLElement; quand: number } | null = null;
  private observateur: MutationObserver | null = null;

  constructor() {
    // Saisies refusees par le serveur : chaque champ nomme tremble avec son message
    effect(() => {
      const champs = this.aide.erreursSaisie();
      if (!champs.length) return;
      queueMicrotask(() => {
        if (this.aide.actif()) {
          for (const c of champs) {
            const champ = this.trouverChamp(c.champ);
            if (champ) this.signalerChamp(champ, c.message);
          }
        }
        this.aide.erreursSaisie.set([]);
      });
    });
  }

  ngOnInit(): void {
    this.aide.charger();
    this.ecran.set(this.cheminCourant());
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.ecran.set(this.cheminCourant());
      this.fermerPanneau();
      this.cacherBulle();
    });
    const sub = this.messages.messageObserver.subscribe(m => this.surMessage(m));
    this.detruit.onDestroy(() => sub.unsubscribe());

    // Ecouteurs hors d'Angular : un survol ne doit pas relancer la detection de changements
    this.zone.runOutsideAngular(() => {
      const ecoute = <K extends keyof DocumentEventMap>(type: K, f: (e: DocumentEventMap[K]) => void, capture = false) => {
        document.addEventListener(type, f, capture);
        this.detruit.onDestroy(() => document.removeEventListener(type, f, capture));
      };
      ecoute('mouseover', e => this.survol(e.target));
      ecoute('focusin', e => this.survol(e.target));
      ecoute('mouseout', e => this.sortie(e.relatedTarget));
      ecoute('focusout', () => this.cacherBulle());
      ecoute('scroll', () => this.cacherBulle(), true);
      ecoute('pointerdown', e => this.appui(e.target), true);
      ecoute('click', e => this.clic(e.target), true);
      ecoute('keydown', e => { if (e.key === 'Escape') this.zone.run(() => { this.cacherBulle(); this.fermerPanneau(); }); });
      ecoute('input', e => this.effacerSignalement(e.target));
      ecoute('change', e => this.effacerSignalement(e.target));
      // « ? » a cote du titre de chaque ecran, remis a chaque changement du contenu
      const main = this.hote.nativeElement.closest('.cadre')?.querySelector('main') ?? document.body;
      let attente = 0;
      this.observateur = new MutationObserver(() => {
        cancelAnimationFrame(attente);
        attente = requestAnimationFrame(() => this.poserBoutonAide(main));
      });
      this.observateur.observe(main, { childList: true, subtree: true });
      this.detruit.onDestroy(() => this.observateur?.disconnect());
      this.poserBoutonAide(main);
    });
  }

  // ------------------------------------------------------------------ 1B : bulles des boutons

  private survol(cible: EventTarget | null): void {
    const el = cible instanceof Element ? cible.closest<HTMLElement>(BOUTONS) : null;
    if (el === this.cible) return;
    this.cacherBulle();
    if (!el || !this.aide.actif() || el.closest('nav, .aide-panneau') || el.classList.contains('aide-q')) return;
    this.cible = el;
    this.minuterie = setTimeout(() => this.montrerBulle(el), DELAI_BULLE_MS);
  }

  private sortie(vers: EventTarget | null): void {
    if (this.cible && !(vers instanceof Node && this.cible.contains(vers))) this.cacherBulle();
  }

  private montrerBulle(el: HTMLElement): void {
    if (!el.isConnected) return;
    const visible = normaliser(el.textContent);
    const nom = el.dataset['aide'] || el.getAttribute('aria-label') || el.getAttribute('ptooltip') || el.getAttribute('title') || visible;
    const trouve = this.aide.bulle(this.ecran(), nom);
    // Sans texte d'aide, un bouton-icone garde au moins son nom ; un bouton deja libelle n'a rien a ajouter
    const aide: TexteAide | null = trouve?.aide ?? (nom && !visible ? { titre: normaliser(nom), texte: '' } : null);
    if (!aide) return;
    const inactif = (el as HTMLButtonElement).disabled || el.getAttribute('aria-disabled') === 'true';
    const r = el.getBoundingClientRect();
    const largeur = 260;
    const dessous = r.top < 130;
    const gauche = Math.max(8, Math.min(r.left + r.width / 2 - largeur / 2, window.innerWidth - largeur - 8));
    this.zone.run(() => this.bulle.set({
      titre: aide.titre, texte: aide.texte, grise: inactif ? (aide.grise ?? 'Bouton inactif pour le moment.') : null,
      haut: dessous ? r.bottom + 10 : r.top - 10, gauche, dessous
    }));
    el.setAttribute('aria-describedby', 'aide-bulle');
    document.documentElement.dataset['bulle'] = 'oui';
  }

  private cacherBulle(): void {
    clearTimeout(this.minuterie);
    if (this.cible?.getAttribute('aria-describedby') === 'aide-bulle') this.cible.removeAttribute('aria-describedby');
    this.cible = null;
    delete document.documentElement.dataset['bulle'];
    if (this.bulle()) this.zone.run(() => this.bulle.set(null));
  }

  // ------------------------------------------------------------------ 2B : panneau d'aide de l'ecran

  private poserBoutonAide(main: Element): void {
    const titre = main.querySelector<HTMLElement>('.entete h1') ?? main.querySelector<HTMLElement>('h1');
    if (!titre || titre.nextElementSibling?.classList.contains('aide-q')) return;
    main.querySelectorAll('.aide-q').forEach(b => b.remove());
    const bouton = document.createElement('button');
    bouton.type = 'button';
    bouton.className = 'aide-q';
    bouton.textContent = '?';
    bouton.setAttribute('aria-label', 'Aide de cet écran');
    bouton.setAttribute('aria-expanded', String(this.panneau()));
    bouton.setAttribute('aria-controls', 'aide-panneau');
    titre.classList.add('aide-titre');
    titre.insertAdjacentElement('afterend', bouton);
  }

  protected basculerPanneau(): void {
    this.panneau() ? this.fermerPanneau() : this.ouvrirPanneau();
  }

  private ouvrirPanneau(): void {
    this.cacherBulle();
    this.edition.set(false);
    this.panneau.set(true);
    document.querySelector('.aide-q')?.setAttribute('aria-expanded', 'true');
    setTimeout(() => document.getElementById('aide-panneau-titre')?.focus());
  }

  protected fermerPanneau(): void {
    if (!this.panneau()) return;
    this.panneau.set(false);
    this.edition.set(false);
    const q = document.querySelector<HTMLElement>('.aide-q');
    q?.setAttribute('aria-expanded', 'false');
  }

  protected modifierTexte(): void {
    this.titreSaisi = this.page().titre;
    this.texteSaisi = this.page().texte;
    this.edition.set(true);
  }

  protected enregistrerTexte(): void {
    this.aide.enregistrer('PAGE', this.ecran(), this.titreSaisi, this.texteSaisi).subscribe(t => {
      this.aide.reecrits.update(l => [...l.filter(x => !(x.type === t.type && x.cle === t.cle)), t]);
      this.edition.set(false);
      this.messages.add({ severity: 'success', summary: "Texte d'aide enregistré" });
    });
  }

  protected retablirTexte(): void {
    this.aide.reinitialiser('PAGE', this.ecran()).subscribe(() => {
      this.aide.reecrits.update(l => l.filter(x => !(x.type === 'PAGE' && x.cle === this.ecran())));
      this.edition.set(false);
      this.messages.add({ severity: 'success', summary: "Texte d'origine rétabli" });
    });
  }

  // ------------------------------------------------------------------ 3C : retours animes pendant la saisie

  /** Un bouton inactif ne recoit pas de clic : l'appui suffit a montrer ce qui manque. */
  private appui(cible: EventTarget | null): void {
    const el = cible instanceof Element ? cible.closest<HTMLButtonElement>(VALIDATION) : null;
    if (el?.disabled) this.verifierObligatoires(el);
  }

  private clic(cible: EventTarget | null): void {
    if (!(cible instanceof Element)) return;
    if (cible.closest('.aide-q')) {
      this.zone.run(() => this.basculerPanneau());
      return;
    }
    const bouton = cible.closest<HTMLElement>(BOUTONS);
    if (bouton) this.dernierClic = { el: bouton, quand: Date.now() };
    const validation = cible.closest<HTMLButtonElement>(VALIDATION);
    if (validation && !validation.disabled) this.verifierObligatoires(validation);
  }

  /** Champs obligatoires vides de la fenetre ou du formulaire du bouton : ils tremblent avec leur message. */
  private verifierObligatoires(bouton: HTMLElement): void {
    if (!this.aide.actif()) return;
    const zone = bouton.closest('.p-dialog, form');
    if (!zone) return;
    zone.querySelectorAll<HTMLInputElement>('input[required], select[required], textarea[required]').forEach(champ => {
      if (!champ.value.trim() && champ.offsetParent !== null) this.signalerChamp(champ, 'Ce champ est obligatoire.');
    });
  }

  /** Champ nomme par le serveur (« lignes[0].montant » : montant), cherche dans la fenetre ouverte, sinon l'ecran. */
  private trouverChamp(nom: string): HTMLElement | null {
    const simple = nom.split('.').pop()!.replace(/\[\d+\]/g, '');
    const fenetres = document.querySelectorAll('.p-dialog');
    const zone = fenetres.length ? fenetres[fenetres.length - 1] : document.querySelector('main');
    if (!zone) return null;
    const echappe = CSS.escape(simple);
    return zone.querySelector<HTMLElement>(`[name="${echappe}"], #${echappe}, [formcontrolname="${echappe}"]`);
  }

  private signalerChamp(champ: HTMLElement, message: string): void {
    champ.classList.remove('aide-champ-ko');
    void champ.offsetWidth; // relance l'animation
    champ.classList.add('aide-champ-ko');
    champ.setAttribute('aria-invalid', 'true');
    let msg = champ.nextElementSibling;
    if (!msg?.classList.contains('aide-champ-msg')) {
      msg = document.createElement('div');
      msg.className = 'aide-champ-msg';
      msg.setAttribute('role', 'alert');
      champ.insertAdjacentElement('afterend', msg);
    }
    msg.textContent = message;
  }

  private effacerSignalement(cible: EventTarget | null): void {
    if (!(cible instanceof HTMLElement) || !cible.classList.contains('aide-champ-ko')) return;
    cible.classList.remove('aide-champ-ko');
    cible.removeAttribute('aria-invalid');
    if (cible.nextElementSibling?.classList.contains('aide-champ-msg')) cible.nextElementSibling.remove();
  }

  /** Reussite annoncee juste apres un clic : une coche se dessine a cote du bouton, s'il est encore a l'ecran. */
  private surMessage(m: ToastMessageOptions | ToastMessageOptions[]): void {
    const succes = (Array.isArray(m) ? m : [m]).some(x => x?.severity === 'success');
    const clic = this.dernierClic;
    if (!succes || !clic || !this.aide.actif() || Date.now() - clic.quand > 15000) return;
    const r = clic.el.isConnected ? clic.el.getBoundingClientRect() : null;
    if (!r || !r.width) return;
    clearTimeout(this.minuterieCoche);
    this.coche.set(null);
    setTimeout(() => {
      this.coche.set({ haut: r.top + r.height / 2 - 14, gauche: Math.min(r.right + 8, window.innerWidth - 36) });
      this.minuterieCoche = setTimeout(() => this.coche.set(null), DUREE_COCHE_MS);
    });
  }

  private cheminCourant(): string {
    let r: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let chemin = '';
    while (r) {
      if (r.routeConfig?.path) chemin = r.routeConfig.path;
      r = r.firstChild;
    }
    return chemin;
  }
}

function normaliser(texte: string | null | undefined): string {
  return (texte ?? '').replace(/\s+/g, ' ').trim();
}
