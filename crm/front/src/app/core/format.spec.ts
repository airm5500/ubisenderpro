import { describe, expect, it } from 'vitest';
import { JourPipe, MontantPipe, TelephonePipe, resumeCriteres } from './format';

describe('format', () => {
  it('affiche les montants a la francaise', () => {
    expect(new MontantPipe().transform(1500000).replace(/\s/g, ' ')).toBe('1 500 000');
    expect(new MontantPipe().transform(null)).toBe('—');
  });

  it('affiche les dates ISO en jj/mm/aaaa', () => {
    expect(new JourPipe().transform('2026-10-18')).toBe('18/10/2026');
    expect(new JourPipe().transform('2026-10-18T09:30:12', true)).toBe('18/10/2026 09:30');
  });

  it('affiche les numeros ivoiriens par paires', () => {
    expect(new TelephonePipe().transform('2250708123456')).toBe('+225 07 08 12 34 56');
    expect(new TelephonePipe().transform(null)).toBe('');
  });

  it('resume les criteres de campagne', () => {
    expect(resumeCriteres({ joursRetardMin: 1, joursRetardMax: 60, soldeMin: 100000, agenceIds: [1, 2] }).map(r => r.replace(/\s/g, ' ')))
      .toEqual(['Échue depuis 1 à 60 j', 'Solde ≥ 100 000', '2 agences']);
  });
});
