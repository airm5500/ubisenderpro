// Informations Clients — non-régression du bug « dateLivraison: "" » :
// une date vide vaut « non renseigné », jamais une erreur technique (500).
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes, suffixe } = require('../aide');

test.describe('Informations clients : dates vides tolérées', () => {
  let h, infoId;
  const s = suffixe();

  test.beforeAll(async ({ request }) => { h = entetes(await connexion(request)); });
  test.afterAll(async ({ request }) => {
    if (infoId) { await request.delete(API + '/infos/' + infoId, { headers: h }); }
  });

  test('création avec TOUTES les dates vides ("") : acceptée', async ({ request }) => {
    const r = await request.post(API + '/infos', {
      headers: h,
      data: { code: 'E2E-INFO-' + s, type: 'INFORMATION_GENERALE', titre: 'Info e2e ' + s,
              message: 'test', canal: 'WEB',
              dateEnvoi: '', dateFinValidite: '', dateLivraison: '', dateResolution: '', dateGarde: '' },
    });
    expect([200, 201]).toContain(r.status());
    const corps = await r.json();
    infoId = corps.id;
    expect(corps.dateLivraison || null).toBeNull();
  });

  test('date réellement invalide : 400 métier citant la valeur, pas un 500', async ({ request }) => {
    const r = await request.post(API + '/infos', {
      headers: h,
      data: { code: 'E2E-INFO-KO-' + s, type: 'INFORMATION_GENERALE', titre: 'X',
              dateLivraison: '29/07/2026' },
    });
    expect(r.status()).toBe(400);
    const corps = await r.json();
    expect(corps.erreur).toContain('29/07/2026');
    expect(corps.erreur).toContain('AAAA-MM-JJ');
  });

  test('titre manquant : la validation métier répond (et non un échec technique)', async ({ request }) => {
    const r = await request.post(API + '/infos', {
      headers: h, data: { code: 'E2E-INFO-SANS-TITRE-' + s, type: 'INFORMATION_GENERALE' },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).champ).toBe('titre');
  });
});
