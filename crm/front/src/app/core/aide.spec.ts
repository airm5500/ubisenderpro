import { describe, expect, it } from 'vitest';
import { decouperTexte } from './aide';
import { AIDE_BOUTONS, AIDE_PAGES, normaliserLibelle } from './aide-textes';
import { routes } from '../app.routes';

describe('aide', () => {
  it('ramene un libelle de bouton a sa cle, sans nombres ni compteurs', () => {
    expect(normaliserLibelle('  Appliquer à 12 client(s) ')).toBe('Appliquer à client(s)');
    expect(normaliserLibelle('Enregistrer (3)')).toBe('Enregistrer');
    expect(normaliserLibelle('Simulation…')).toBe('Simulation');
    expect(normaliserLibelle('Envoyer à 2 destinataire(s)')).toBe('Envoyer à destinataire(s)');
    expect(normaliserLibelle(null)).toBe('');
  });

  it('decoupe un texte d aide en paragraphes et listes', () => {
    expect(decouperTexte('Premier paragraphe\nqui continue.\n\n1. un\n2. deux\n- puce\nFin.')).toEqual([
      { type: 'p', texte: 'Premier paragraphe qui continue.' },
      { type: 'ol', lignes: ['un', 'deux'] },
      { type: 'ul', lignes: ['puce'] },
      { type: 'p', texte: 'Fin.' }
    ]);
    expect(decouperTexte('')).toEqual([]);
  });

  it('chaque ecran du menu a son panneau d aide', () => {
    const chemins = routes.flatMap(r => r.children ?? []).map(r => r.path!).filter(p => p);
    const sansAide = chemins.filter(c => !AIDE_PAGES[c]);
    expect(sansAide).toEqual([]);
  });

  it('les cles des bulles sont deja normalisees et visent un ecran connu', () => {
    const chemins = new Set(['*', 'connexion', ...routes.flatMap(r => r.children ?? []).map(r => r.path!)]);
    for (const cle of Object.keys(AIDE_BOUTONS)) {
      const [ecran, libelle] = cle.split('|');
      expect(chemins.has(ecran), cle).toBe(true);
      expect(normaliserLibelle(libelle), cle).toBe(libelle);
      expect(AIDE_BOUTONS[cle].texte.length, cle).toBeGreaterThan(10);
    }
  });
});
