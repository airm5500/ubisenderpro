// Authentification : le socle de toute la sécurité applicative.
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes, MOT_DE_PASSE, LOGIN } = require('../aide');

test.describe('Authentification', () => {

  test('identifiants valides : jeton + profil', async ({ request }) => {
    const r = await request.post(API + '/auth/login', {
      data: { login: LOGIN, motDePasse: MOT_DE_PASSE },
    });
    expect(r.status()).toBe(200);
    const corps = await r.json();
    expect(corps.token).toBeTruthy();
    expect(corps.user.login).toBe(LOGIN);
    expect(Array.isArray(corps.user.roles)).toBe(true);
  });

  test('mot de passe faux : 401, aucun jeton', async ({ request }) => {
    const r = await request.post(API + '/auth/login', {
      data: { login: LOGIN, motDePasse: 'MAUVAIS-' + Date.now() },
    });
    expect(r.status()).toBe(401);
    expect((await r.json()).token).toBeUndefined();
  });

  test('endpoint protégé sans jeton : refusé', async ({ request }) => {
    const r = await request.get(API + '/clients');
    expect([401, 403]).toContain(r.status());
  });

  test('endpoint protégé avec jeton fantaisiste : refusé', async ({ request }) => {
    const r = await request.get(API + '/clients', {
      headers: { Authorization: 'Bearer nimporte-quoi' },
    });
    expect([401, 403]).toContain(r.status());
  });

  test('avec jeton valide : la liste des clients répond', async ({ request }) => {
    const token = await connexion(request);
    const r = await request.get(API + '/clients?limit=1', { headers: entetes(token) });
    expect(r.status()).toBe(200);
    const page = await r.json();
    expect(page).toHaveProperty('data');
    expect(page).toHaveProperty('total');
  });
});
