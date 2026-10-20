// Niveau 2 - E2E API : aide et guidage (interrupteurs, textes reecrits), droits et codes d'erreur.
const { test, expect } = require('@playwright/test');
const { LOGIN } = require('../aide');
const { Session, json, donnees } = require('../comptes');

let jeu;
test.beforeAll(async ({ baseURL }) => { jeu = await donnees(baseURL); });

test('l aide se lit connecte seulement', async ({ baseURL }) => {
  const anonyme = await Session.anonyme(baseURL);
  expect((await anonyme.get('api/aide')).status()).toBe(401);
  await anonyme.fermer();
  const agent = await Session.de(baseURL, jeu.comptes.agentYop);
  const aide = await json(await agent.get('api/aide'));
  expect(aide.guidageEntreprise).toBe(true);
  expect(Array.isArray(aide.textes)).toBe(true);
  await agent.fermer();
});

test('chaque utilisateur coupe le guidage pour lui seul', async ({ baseURL }) => {
  const agent = await Session.de(baseURL, jeu.comptes.agentYop);
  const marketing = await Session.de(baseURL, jeu.comptes.chargeMarketing);
  try {
    const r = await agent.request.put('api/aide/preference', { data: { actif: false }, headers: await agent.entetes() });
    expect((await json(r)).guidageUtilisateur).toBe(false);
    expect((await json(await marketing.get('api/aide'))).guidageUtilisateur).toBe(true);
    // Sans jeton CSRF : refuse
    expect((await agent.request.put('api/aide/preference', { data: { actif: true } })).status()).toBe(403);
    // Corps incomplet : 400 avec le champ en cause
    const vide = await agent.request.put('api/aide/preference', { data: {}, headers: await agent.entetes() });
    expect(vide.status()).toBe(400);
    expect((await vide.json()).champs[0].champ).toBe('actif');
  } finally {
    await agent.request.put('api/aide/preference', { data: { actif: true }, headers: await agent.entetes() });
    await agent.fermer();
    await marketing.fermer();
  }
});

test('seul le droit Parametres reecrit et retablit les textes', async ({ baseURL }) => {
  const admin = await Session.de(baseURL, LOGIN);
  const agent = await Session.de(baseURL, jeu.comptes.agentYop);
  const corps = { type: 'BOUTON', cle: 'factures|E2E-bouton', titre: 'E2E titre', texte: 'E2E texte de la bulle' };
  try {
    expect((await agent.request.put('api/aide/textes', { data: corps, headers: await agent.entetes() })).status()).toBe(403);
    const ecrit = await json(await admin.request.put('api/aide/textes', { data: corps, headers: await admin.entetes() }));
    expect(ecrit.titre).toBe('E2E titre');
    const vu = (await json(await agent.get('api/aide'))).textes.find(t => t.cle === corps.cle);
    expect(vu.texte).toBe('E2E texte de la bulle');
    expect((await agent.post('api/aide/textes/reinitialisation', { type: 'BOUTON', cle: corps.cle })).status()).toBe(403);
    // Type inconnu et titre vide : deux champs refuses
    const mauvais = await admin.request.put('api/aide/textes', { data: { ...corps, type: 'MENU', titre: ' ' }, headers: await admin.entetes() });
    expect(mauvais.status()).toBe(400);
    expect((await mauvais.json()).champs.map(c => c.champ).sort()).toEqual(['titre', 'type']);
  } finally {
    await json(await admin.post('api/aide/textes/reinitialisation', { type: 'BOUTON', cle: corps.cle }), 204);
    await admin.fermer();
    await agent.fermer();
  }
});
