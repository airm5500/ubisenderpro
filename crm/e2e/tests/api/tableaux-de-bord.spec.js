// Niveau 2 - E2E API : calendrier d'activite des tableaux de bord, parametre du menu repliable, historique des envois.
// Droits par profil, cloisonnement par agence et codes d'erreur.
const { test, expect, request: fabrique } = require('@playwright/test');
const { connexion, suffixe } = require('../aide');
const { jour, ecrire, ok, sessionDe, creerCompte, creerAgenceAvecFacture, parametre } = require('../tableaux-donnees');

test.describe.configure({ mode: 'serial' });

let admin;
let donnees;
let directeurAutreAgence;
let chargeMarketing;
const periode = { du: jour(-2), au: jour(10) };
const salon = `E2E Salon ${suffixe()}`;

test.beforeAll(async ({}, info) => {
  const baseURL = info.project.use.baseURL;
  admin = await fabrique.newContext({ baseURL });
  await connexion(admin);
  donnees = await creerAgenceAvecFacture(admin);
  // Paiement partiel aujourd'hui (100 000 -> 40 000) et promesse pour dans 3 jours
  await ok(admin, 'put', `api/recouvrement/factures/${donnees.facture.id}`, { ...donnees.facture, solde: 40000 });
  await ok(admin, 'post', `api/recouvrement/factures/${donnees.facture.id}/suivi`, { suivi: 'PROMESSE', datePromesse: jour(3), montantPromis: 25000 });
  const directeur = await creerCompte(admin, baseURL, 'dir', 'DIR_AGENCE', 'RECOUVREMENT', [1]);
  directeurAutreAgence = await sessionDe(baseURL, directeur.login, directeur.motDePasse);
  const charge = await creerCompte(admin, baseURL, 'mkt', 'CHARGE_MARKETING', 'MARKETING', []);
  chargeMarketing = await sessionDe(baseURL, charge.login, charge.motDePasse);
});

test.afterAll(async () => {
  if (admin) { await parametre(admin, 'charte.menu_repliable', 'NON'); }
  for (const r of [admin, directeurAutreAgence, chargeMarketing]) { if (r) { await r.dispose(); } }
});

const calendrier = (request, module, params) => request.get(`api/${module}/tableau-de-bord/calendrier`, { params });

test('le calendrier du recouvrement donne les activites de chaque jour sur le perimetre demande', async () => {
  const r = await calendrier(admin, 'recouvrement', { ...periode, agenceId: donnees.agence.id });
  expect(r.status()).toBe(200);
  const activites = await r.json();
  const de = type => activites.filter(a => a.type === type);
  expect(de('ENCAISSEMENT')).toEqual([expect.objectContaining({ date: jour(0), montant: 60000, nombre: 1, lien: '/factures' })]);
  expect(de('ECHEANCE')).toEqual([expect.objectContaining({ date: jour(5), montant: 40000, libelle: '1 facture à échéance' })]);
  expect(de('PROMESSE')).toEqual([expect.objectContaining({ date: jour(0), nombre: 1 })]);
  expect(de('PROMESSE_ECHEANCE')).toEqual([expect.objectContaining({ date: jour(3), montant: 25000 })]);
  const dates = activites.map(a => a.date);
  expect([...dates].sort()).toEqual(dates);
});

test('par defaut le calendrier couvre le mois en cours', async () => {
  const activites = await (await calendrier(admin, 'recouvrement')).json();
  for (const a of activites) { expect(a.date.substring(0, 7)).toBe(jour(0).substring(0, 7)); }
});

test("un directeur d'une autre agence ne voit rien de cette agence", async () => {
  const r = await calendrier(directeurAutreAgence, 'recouvrement', { ...periode, agenceId: donnees.agence.id });
  expect(r.status()).toBe(200);
  expect(await r.json()).toEqual([]);
});

test('chaque calendrier reste reserve aux titulaires du droit du tableau de bord', async ({ request }) => {
  expect((await calendrier(directeurAutreAgence, 'marketing')).status()).toBe(403);
  expect((await calendrier(chargeMarketing, 'recouvrement')).status()).toBe(403);
  expect((await calendrier(chargeMarketing, 'marketing', periode)).status()).toBe(200);
  // Sans session
  expect((await calendrier(request, 'recouvrement')).status()).toBe(401);
  expect((await calendrier(request, 'marketing')).status()).toBe(401);
});

test("le calendrier du marketing retrouve chaque jour d'un evenement", async () => {
  await ok(admin, 'post', 'api/marketing/evenements', { nom: salon, nature: 'AUTRE', dateDebut: jour(4), dateFin: jour(5) });
  const activites = await (await calendrier(chargeMarketing, 'marketing', periode)).json();
  const jours = activites.filter(a => a.libelle === salon);
  expect(jours.map(a => a.date)).toEqual([jour(4), jour(5)]);
  expect(jours[0]).toEqual(expect.objectContaining({ type: 'EVENEMENT', lien: '/marketing/evenements' }));
});

test('une periode invalide est refusee avec un message clair', async () => {
  const inverse = await calendrier(admin, 'recouvrement', { du: jour(5), au: jour(0) });
  expect(inverse.status()).toBe(400);
  expect((await inverse.json()).message).toBe('Le début de la période doit précéder sa fin.');
  const tropLongue = await calendrier(admin, 'marketing', { du: jour(0), au: jour(63) });
  expect(tropLongue.status()).toBe(400);
  expect((await tropLongue.json()).message).toBe('Le calendrier couvre au plus 62 jours.');
  expect((await calendrier(admin, 'recouvrement', { du: '2026-13-45' })).status()).toBe(400);
});

test('le menu repliable est desactive par defaut, se regle par un administrateur et se lit avant la connexion', async ({ request }) => {
  await parametre(admin, 'charte.menu_repliable', 'NON');
  expect((await (await request.get('api/charte')).json()).menuRepliable).toBe(false);
  await parametre(admin, 'charte.menu_repliable', 'OUI');
  expect((await (await request.get('api/charte')).json()).menuRepliable).toBe(true);
  expect((await ecrire(chargeMarketing, 'put', 'api/parametres', [{ cle: 'charte.menu_repliable', valeur: 'NON' }])).status()).toBe(403);
  await parametre(admin, 'charte.menu_repliable', 'NON');
});

test("l'historique des envois donne le texte complet de chaque message pour sa visualisation", async () => {
  const r = await admin.get('api/historique-envois', { params: { du: jour(-30), page: 0, taille: 5 } });
  expect(r.status()).toBe(200);
  for (const e of (await r.json()).elements) {
    expect(Object.keys(e)).toEqual(expect.arrayContaining(['contenu', 'canal', 'statut', 'destinataire', 'origine']));
  }
});
