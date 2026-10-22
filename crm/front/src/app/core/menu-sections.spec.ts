import { describe, expect, it } from 'vitest';
import { apresClic, sectionOuverte } from './menu-sections';

describe('menu a sections repliables', () => {
  it('laisse toutes les sections ouvertes quand le parametre est desactive', () => {
    expect(sectionOuverte(false, null, 'sAdmin', false)).toBe(true);
    expect(sectionOuverte(false, '', 'sAdmin', false)).toBe(true);
    expect(apresClic(false, null, 'sAdmin', false)).toBe(null);
  });

  it("ouvre d'office la section de l'ecran affiche", () => {
    expect(sectionOuverte(true, null, 'sEchanges', true)).toBe(true);
    expect(sectionOuverte(true, null, 'sAdmin', false)).toBe(false);
  });

  it("ouvrir une section ferme l'autre", () => {
    const ouverte = apresClic(true, null, 'sAdmin', false);
    expect(ouverte).toBe('sAdmin');
    expect(sectionOuverte(true, ouverte, 'sAdmin', false)).toBe(true);
    expect(sectionOuverte(true, ouverte, 'sEchanges', true)).toBe(false);
  });

  it('un second clic referme la section ouverte', () => {
    expect(apresClic(true, 'sAdmin', 'sAdmin', false)).toBe('');
    expect(apresClic(true, null, 'sEchanges', true)).toBe('');
    expect(sectionOuverte(true, '', 'sEchanges', true)).toBe(false);
  });
});
