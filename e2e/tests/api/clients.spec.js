// Comptes clients : cycle de vie complet + mise à jour sélective.
// Toutes les données créées sont préfixées E2E- puis supprimées.
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes, suffixe } = require('../aide');

test.describe('Comptes clients', () => {
  let token, h;
  const s = suffixe();
  const codes = ['E2E-A-' + s, 'E2E-B-' + s];
  const ids = [];

  test.beforeAll(async ({ request }) => {
    token = await connexion(request);
    h = entetes(token);
  });

  test.afterAll(async ({ request }) => {
    for (const id of ids) {
      await request.delete(API + '/clients/' + id, { headers: h });
    }
  });

  test('création : champs obligatoires contrôlés avec le champ fautif', async ({ request }) => {
    const r = await request.post(API + '/clients', {
      headers: h, data: { nomCompte: 'SANS CODE' },
    });
    expect(r.status()).toBe(400);
    const corps = await r.json();
    expect(corps.erreur).toContain('obligatoire');
    expect(corps.champ).toBe('numeroClient');
  });

  test('création de deux comptes de test', async ({ request }) => {
    for (const code of codes) {
      const r = await request.post(API + '/clients', {
        headers: h,
        data: { numeroClient: code, nomCompte: 'PHARMACIE ' + code, entreprise: 'PHCIE E2E',
                agence: 'E2E-AGENCE', region: 'E2E-REGION', emailPrincipal: 'e2e@exemple.ci' },
      });
      expect(r.status()).toBe(201);
      ids.push((await r.json()).id);
    }
    expect(ids.length).toBe(2);
  });

  test('doublon de numéro client : refusé (déjà utilisé)', async ({ request }) => {
    const r = await request.post(API + '/clients', {
      headers: h, data: { numeroClient: codes[0], nomCompte: 'DOUBLON' },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).erreur).toContain('déjà utilisé');
  });

  test('e-mail mal formé : refusé avec le champ fautif', async ({ request }) => {
    const r = await request.post(API + '/clients', {
      headers: h,
      data: { numeroClient: 'E2E-MAIL-' + s, nomCompte: 'X', emailPrincipal: 'pas-un-mail' },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).champ).toBe('emailPrincipal');
  });

  test('recherche : le compte créé se retrouve par son code', async ({ request }) => {
    const r = await request.get(API + '/clients?q=' + codes[0], { headers: h });
    const page = await r.json();
    expect(page.data.some(c => c.numeroClient === codes[0])).toBe(true);
  });

  test('mise à jour sélective : la tournée change, le reste est intact', async ({ request }) => {
    const r = await request.post(API + '/clients/maj-selective', {
      headers: h, data: { ids, champs: { tournee: 'E2E-T1' } },
    });
    expect(r.status()).toBe(200);
    expect((await r.json()).modifies).toBe(2);

    const relu = await (await request.get(API + '/clients/' + ids[0], { headers: h })).json();
    expect(relu.tournee).toBe('E2E-T1');
    expect(relu.agence).toBe('E2E-AGENCE'); // non coché => non touché
  });

  test('mise à jour sélective : refus sans compte ou sans champ', async ({ request }) => {
    const sansIds = await request.post(API + '/clients/maj-selective', {
      headers: h, data: { ids: [], champs: { tournee: 'X' } },
    });
    expect(sansIds.status()).toBe(400);
    const sansChamp = await request.post(API + '/clients/maj-selective', {
      headers: h, data: { ids, champs: {} },
    });
    expect(sansChamp.status()).toBe(400);
  });

  test('le filtre tournée renvoie les comptes déplacés', async ({ request }) => {
    const r = await request.get(API + '/clients?tournee=E2E-T1', { headers: h });
    const page = await r.json();
    expect(page.data.filter(c => codes.includes(c.numeroClient)).length).toBe(2);
  });

  test('désactivation / réactivation', async ({ request }) => {
    const off = await request.post(API + '/clients/' + ids[0] + '/deactivate', { headers: h });
    expect(off.status()).toBe(200);
    let relu = await (await request.get(API + '/clients/' + ids[0], { headers: h })).json();
    expect(relu.actif).toBe(false);
    const on = await request.post(API + '/clients/' + ids[0] + '/activate', { headers: h });
    expect(on.status()).toBe(200);
    relu = await (await request.get(API + '/clients/' + ids[0], { headers: h })).json();
    expect(relu.actif).toBe(true);
  });
});
