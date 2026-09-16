// Listes de diffusion : import assisté (simulation puis application), vidage.
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes, suffixe } = require('../aide');

test.describe('Listes de diffusion', () => {
  let token, h, listeId, clientId;
  const s = suffixe();
  const codeClient = 'E2E-LST-' + s;

  test.beforeAll(async ({ request }) => {
    token = await connexion(request);
    h = entetes(token);
    // Un client AVEC contact : l'import ajoute le contact principal.
    const c = await request.post(API + '/clients', {
      headers: h, data: { numeroClient: codeClient, nomCompte: 'PHARMACIE LISTE ' + s },
    });
    expect(c.status()).toBe(201);
    clientId = (await c.json()).id;
    // Contrat REST : le corps est directement le TABLEAU des numéros.
    const n = await request.post(API + '/clients/' + clientId + '/numeros', {
      headers: h, data: [{ numero: '2250700000001', whatsapp: true, principal: true }],
    });
    expect(n.status()).toBe(204);
    const l = await request.post(API + '/lists', {
      headers: h, data: { nom: 'E2E Liste ' + s, description: 'jeu de test e2e', actif: true },
    });
    expect(l.status()).toBe(201);
    listeId = (await l.json()).id;
  });

  test.afterAll(async ({ request }) => {
    if (listeId) { await request.delete(API + '/lists/' + listeId + '/contacts', { headers: h }); }
    if (clientId) { await request.delete(API + '/clients/' + clientId, { headers: h }); }
  });

  test('simulation : rapport complet, rien n\'est écrit', async ({ request }) => {
    const r = await request.post(API + '/lists/' + listeId + '/import-codes', {
      headers: h, data: { codes: [codeClient, 'CODE-INTROUVABLE-' + s], simulation: true },
    });
    expect(r.status()).toBe(200);
    const rapport = await r.json();
    expect(rapport.lignesLues).toBe(2);
    expect(rapport.ajoutes).toBe(1);
    expect(rapport.introuvables).toBe(1);
    expect(rapport.exemplesIntrouvables).toContain('CODE-INTROUVABLE-' + s);
    // Simulation : la liste doit être restée vide.
    const membres = await (await request.get(API + '/lists/' + listeId + '/contacts', { headers: h })).json();
    expect(membres.length).toBe(0);
  });

  test('import réel puis ré-import : « déjà membre » compté, pas de doublon', async ({ request }) => {
    const r1 = await request.post(API + '/lists/' + listeId + '/import-codes', {
      headers: h, data: { codes: [codeClient], simulation: false },
    });
    expect((await r1.json()).ajoutes).toBe(1);
    const r2 = await request.post(API + '/lists/' + listeId + '/import-codes', {
      headers: h, data: { codes: [codeClient], simulation: false },
    });
    const rapport2 = await r2.json();
    expect(rapport2.ajoutes).toBe(0);
    expect(rapport2.dejaPresents).toBe(1);
    const membres = await (await request.get(API + '/lists/' + listeId + '/contacts', { headers: h })).json();
    expect(membres.length).toBe(1);
  });

  test('vider la liste : membres retirés, liste conservée', async ({ request }) => {
    const r = await request.delete(API + '/lists/' + listeId + '/contacts', { headers: h });
    expect(r.status()).toBe(200);
    expect((await r.json()).retires).toBe(1);
    const membres = await (await request.get(API + '/lists/' + listeId + '/contacts', { headers: h })).json();
    expect(membres.length).toBe(0);
    const listes = await (await request.get(API + '/lists', { headers: h })).json();
    expect(listes.some(l => l.id === listeId)).toBe(true);
  });
});
