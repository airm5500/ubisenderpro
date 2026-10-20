import { describe, expect, it } from 'vitest';
import { ActiviteCalendrier, bornes, iso, premierDuMois, texteCourt } from './calendrier-activite';

const activite = (a: Partial<ActiviteCalendrier>): ActiviteCalendrier =>
  ({ date: '2026-10-20', type: 'RELANCES', libelle: '', nombre: null, montant: null, lien: null, ...a });

describe("calendrier d'activite", () => {
  it('couvre des semaines completes, du lundi au dimanche', () => {
    const [du, au] = bornes(new Date(Date.UTC(2026, 9, 1)));
    expect(iso(du)).toBe('2026-09-28');
    expect(iso(au)).toBe('2026-11-01');
    expect(du.getUTCDay()).toBe(1);
    expect(au.getUTCDay()).toBe(0);
  });

  it("ne deborde pas quand le mois commence un lundi et finit un dimanche", () => {
    const [du, au] = bornes(new Date(Date.UTC(2027, 1, 1)));
    expect(iso(du)).toBe('2027-02-01');
    expect(iso(au)).toBe('2027-02-28');
  });

  it('reste dans la limite de 62 jours du serveur', () => {
    for (let m = 0; m < 24; m++) {
      const [du, au] = bornes(new Date(Date.UTC(2026, m, 1)));
      expect((au.getTime() - du.getTime()) / 86400000).toBeLessThanOrEqual(62);
    }
  });

  it('part du premier jour du mois', () => {
    expect(iso(premierDuMois(new Date(2026, 9, 20, 15)))).toBe('2026-10-01');
  });

  it('resume chaque activite dans sa pastille', () => {
    expect(texteCourt(activite({ type: 'RELANCES', nombre: 12, libelle: '12 relances envoyées, 3 lus' }))).toBe('12');
    expect(texteCourt(activite({ type: 'ENCAISSEMENT', nombre: 1, montant: 75000 })).replace(/\s/g, ' ')).toBe('75 k');
    expect(texteCourt(activite({ type: 'CAMPAGNE', libelle: 'Lancement : Relance J+15' }))).toBe('Relance J+15');
    expect(texteCourt(activite({ type: 'CAMPAGNE_PROGRAMMEE', libelle: 'Programmée : Relance novembre' }))).toBe('Relance novembre');
    expect(texteCourt(activite({ type: 'PROMOTION_FIN', libelle: 'Fin de la promotion Toussaint' }))).toBe('Toussaint');
    expect(texteCourt(activite({ type: 'EVENEMENT', libelle: 'Noël' }))).toBe('Noël');
  });
});
