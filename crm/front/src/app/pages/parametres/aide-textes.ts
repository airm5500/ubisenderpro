import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { AideService, TypeAide } from '../../core/aide';
import { AIDE_BOUTONS, AIDE_PAGES } from '../../core/aide-textes';
import { JourPipe } from '../../core/format';

interface Ligne { type: TypeAide; cle: string; ecran: string; libelle: string; titre: string; texte: string; reecrit: boolean; modifiePar: string | null; modifieLe: string | null; }

/** Ecran d'une cle : titre de son aide, « Tous les écrans » pour les boutons communs. */
function nomEcran(chemin: string): string {
  if (chemin === '*') return 'Tous les écrans';
  return AIDE_PAGES[chemin]?.titre ?? chemin;
}

/** Parametres > Aide et guidage : textes des panneaux « ? » et des bulles, a reecrire ou a remettre d'origine. */
@Component({
  selector: 'app-aide-textes',
  imports: [FormsModule, DialogModule, TooltipModule, JourPipe],
  templateUrl: './aide-textes.html'
})
export class AideTextes {
  private readonly aide = inject(AideService);
  private readonly messages = inject(MessageService);

  protected readonly type = signal<TypeAide>('PAGE');
  protected readonly recherche = signal('');
  protected readonly cible = signal<Ligne | null>(null);
  protected readonly enCours = signal(false);
  protected titreSaisi = '';
  protected texteSaisi = '';

  protected readonly lignes = computed<Ligne[]>(() => {
    const type = this.type();
    const cles = new Set([...Object.keys(type === 'PAGE' ? AIDE_PAGES : AIDE_BOUTONS),
      ...this.aide.reecrits().filter(t => t.type === type).map(t => t.cle)]);
    const q = this.recherche().trim().toLowerCase();
    return [...cles].map(cle => {
      const texte = this.aide.texte(type, cle)!;
      const reecrit = this.aide.reecrit(type, cle);
      const [chemin, libelle] = type === 'PAGE' ? [cle, ''] : cle.split('|');
      return { type, cle, ecran: nomEcran(chemin), libelle, titre: texte.titre, texte: texte.texte, reecrit: !!reecrit,
        modifiePar: reecrit?.modifiePar ?? null, modifieLe: reecrit?.modifieLe ?? null };
    })
      .filter(l => !q || `${l.ecran} ${l.libelle} ${l.titre} ${l.texte}`.toLowerCase().includes(q))
      .sort((a, b) => a.ecran.localeCompare(b.ecran, 'fr') || a.libelle.localeCompare(b.libelle, 'fr'));
  });

  protected modifier(l: Ligne): void {
    this.titreSaisi = l.titre;
    this.texteSaisi = l.texte;
    this.cible.set(l);
  }

  protected enregistrer(): void {
    const l = this.cible();
    if (!l) return;
    this.enCours.set(true);
    this.aide.enregistrer(l.type, l.cle, this.titreSaisi, this.texteSaisi).subscribe({
      next: t => {
        this.aide.reecrits.update(liste => [...liste.filter(x => !(x.type === t.type && x.cle === t.cle)), t]);
        this.enCours.set(false);
        this.cible.set(null);
        this.messages.add({ severity: 'success', summary: "Texte d'aide enregistré" });
      },
      error: () => this.enCours.set(false)
    });
  }

  protected retablir(l: Ligne): void {
    this.aide.reinitialiser(l.type, l.cle).subscribe(() => {
      this.aide.reecrits.update(liste => liste.filter(x => !(x.type === l.type && x.cle === l.cle)));
      this.messages.add({ severity: 'success', summary: "Texte d'origine rétabli" });
    });
  }
}
