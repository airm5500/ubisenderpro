import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from './api';
import { AIDE_BOUTONS, AIDE_PAGES, TexteAide, normaliserLibelle } from './aide-textes';

export type TypeAide = 'BOUTON' | 'PAGE';
export interface ChampEnErreur { champ: string; message: string; }

export interface AideTexte { type: TypeAide; cle: string; titre: string; texte: string; modifiePar: string | null; modifieLe: string | null; }
interface Aide { guidageEntreprise: boolean; guidageUtilisateur: boolean; textes: AideTexte[]; }

/** Morceau d'un texte d'aide : paragraphe, liste numerotee ou liste a puces. */
export type Bloc = { type: 'p'; texte: string } | { type: 'ol' | 'ul'; lignes: string[] };

/** Decoupe un texte d'aide : lignes « 1. … » en liste numerotee, « - … » en liste a puces, ligne vide entre paragraphes. */
export function decouperTexte(texte: string): Bloc[] {
  const blocs: Bloc[] = [];
  for (const ligne of (texte ?? '').split(/\r?\n/).map(l => l.trim())) {
    const numero = /^\d+[.)]\s+(.*)$/.exec(ligne);
    const puce = /^[-•]\s+(.*)$/.exec(ligne);
    const dernier = blocs.at(-1);
    if (numero || puce) {
      const type = numero ? 'ol' : 'ul';
      const contenu = (numero ?? puce)![1];
      if (dernier && dernier.type === type) dernier.lignes.push(contenu);
      else blocs.push({ type, lignes: [contenu] });
    } else if (!ligne) {
      if (dernier?.type === 'p') blocs.push({ type: 'p', texte: '' });
    } else if (dernier?.type === 'p' && dernier.texte) {
      dernier.texte += ' ' + ligne;
    } else if (dernier?.type === 'p') {
      dernier.texte = ligne;
    } else {
      blocs.push({ type: 'p', texte: ligne });
    }
  }
  return blocs.filter(bl => bl.type !== 'p' || bl.texte);
}

/**
 * Textes d'aide (origine + reecritures de l'administrateur) et interrupteurs du guidage.
 * Le panneau « ? » reste toujours disponible ; les bulles et animations suivent les interrupteurs.
 */
@Injectable({ providedIn: 'root' })
export class AideService {
  private readonly api = inject(Api);

  readonly guidageEntreprise = signal(true);
  readonly guidageUtilisateur = signal(true);
  readonly reecrits = signal<AideTexte[]>([]);
  readonly actif = computed(() => this.guidageEntreprise() && this.guidageUtilisateur());
  /** Derniers champs refuses par le serveur (erreur 400), montres sur l'ecran par le guidage. */
  readonly erreursSaisie = signal<ChampEnErreur[]>([]);

  private readonly index = computed(() => new Map(this.reecrits().map(t => [`${t.type}#${t.cle}`, t])));

  charger(): void {
    this.api.getSilencieux<Aide>('/api/aide').subscribe({ next: a => this.appliquer(a), error: () => this.appliquer(null) });
  }

  /** Choix personnel de l'utilisateur connecte. */
  changerPreference(actif: boolean): void {
    this.guidageUtilisateur.set(actif);
    this.marquer();
    this.api.put<Aide>('/api/aide/preference', { actif }).subscribe(a => this.appliquer(a));
  }

  enregistrer(type: TypeAide, cle: string, titre: string, texte: string) {
    return this.api.put<AideTexte>('/api/aide/textes', { type, cle, titre, texte });
  }

  reinitialiser(type: TypeAide, cle: string) {
    return this.api.post<void>('/api/aide/textes/reinitialisation', { type, cle });
  }

  /** Texte reecrit en base, sinon celui livre avec l'ecran. */
  texte(type: TypeAide, cle: string): TexteAide | null {
    const reecrit = this.index().get(`${type}#${cle}`);
    const origine = type === 'PAGE' ? AIDE_PAGES[cle] : AIDE_BOUTONS[cle];
    if (reecrit) return { titre: reecrit.titre, texte: reecrit.texte, grise: origine?.grise };
    return origine ?? null;
  }

  reecrit(type: TypeAide, cle: string): AideTexte | undefined {
    return this.index().get(`${type}#${cle}`);
  }

  /** Bulle d'un bouton : propre a l'ecran d'abord, commune ensuite. */
  bulle(ecran: string, libelle: string): { cle: string; aide: TexteAide } | null {
    const nom = normaliserLibelle(libelle);
    if (!nom) return null;
    for (const cle of [`${ecran}|${nom}`, `*|${nom}`]) {
      const aide = this.texte('BOUTON', cle);
      if (aide) return { cle, aide };
    }
    return null;
  }

  private appliquer(a: Aide | null): void {
    if (a) {
      this.guidageEntreprise.set(a.guidageEntreprise);
      this.guidageUtilisateur.set(a.guidageUtilisateur);
      this.reecrits.set(a.textes);
    }
    this.marquer();
  }

  /** html[data-guidage] : les animations de saisie ne jouent qu'avec le guidage actif. */
  marquer(): void {
    document.documentElement.dataset['guidage'] = this.actif() ? 'oui' : 'non';
  }
}
