// Niveau 2 - E2E API : modeles de messages (modeles de depart, modification, export Word, erreurs).
const { test, expect } = require('@playwright/test');
const { connexion, suffixe } = require('../aide');

const MARKETING = 'api/marketing/modeles';
const RECOUVREMENT = 'api/recouvrement/modeles-messages';

const DEPART_MARKETING = ['Promo · Annonce mensuelle', 'Promo · Lancement', 'Promo · Derniers jours', 'Promo · Dernière chance',
  'Dispo · Produits disponibles', 'Dispo · Retour de rupture', 'Dispo · Stock limité', 'Dispo · Risque de rupture',
  'Dispo · Rupture confirmée', 'Info · Rappel de garde', 'Anniversaire client'];
const DEPART_RECOUVREMENT = ['Relance préventive', 'Relance échue'];

let entetes;
test.beforeEach(async ({ request }) => { entetes = await connexion(request); });

async function noms(request, base) {
  const r = await request.get(base);
  expect(r.status()).toBe(200);
  return (await r.json()).map(m => m.nom);
}

test('chaque module propose ses modeles de depart, et seulement les siens', async ({ request }) => {
  const marketing = await noms(request, MARKETING);
  const recouvrement = await noms(request, RECOUVREMENT);
  for (const nom of DEPART_MARKETING) { expect(marketing).toContain(nom); }
  for (const nom of DEPART_RECOUVREMENT) { expect(recouvrement).toContain(nom); }
  for (const nom of DEPART_RECOUVREMENT) { expect(marketing).not.toContain(nom); }
  for (const nom of DEPART_MARKETING) { expect(recouvrement).not.toContain(nom); }
});

test('un modele de depart s exporte en Word', async ({ request }) => {
  const modeles = await (await request.get(RECOUVREMENT)).json();
  const echue = modeles.find(m => m.nom === 'Relance échue');
  expect(echue.typeCampagne).toBe('APRES_ECHEANCE');
  expect(echue.contenu).toContain('Bonjour Docteur *{nom_client}*');
  const r = await request.get(`${RECOUVREMENT}/${echue.id}/word`);
  expect(r.status()).toBe(200);
  expect(r.headers()['content-disposition']).toContain('.docx');
  // Un .docx est une archive zip
  expect((await r.body()).subarray(0, 2).toString()).toBe('PK');
});

test('une copie d un modele de depart se modifie puis s archive', async ({ request }) => {
  const lancement = (await (await request.get(MARKETING)).json()).find(m => m.nom === 'Promo · Lancement');
  const nom = `E2E-Lancement ${suffixe()}`;
  const cree = await request.post(MARKETING, { headers: entetes,
    data: { nom, typeCampagne: 'PROMOTION', canal: 'WHATSAPP', contenu: lancement.contenu } });
  expect(cree.status()).toBe(200);
  const copie = await cree.json();

  const contenu = copie.contenu.replace('*Direction Commerciale*', '*Direction Commerciale E2E*');
  const modifie = await request.put(`${MARKETING}/${copie.id}`, { headers: entetes,
    data: { nom, typeCampagne: 'PROMOTION', canal: 'WHATSAPP', contenu } });
  expect(modifie.status()).toBe(200);
  expect((await modifie.json()).contenu).toContain('Direction Commerciale E2E');

  const archive = await request.post(`${MARKETING}/${copie.id}/archivage`, { headers: entetes });
  expect((await archive.json()).statut).toBe('ARCHIVE');
});

test('erreurs : session absente, jeton CSRF absent, variable interdite ou inconnue, nom deja pris', async ({ request, playwright, baseURL }) => {
  const anonyme = await playwright.request.newContext({ baseURL });
  expect((await anonyme.get(MARKETING)).status()).toBe(401);
  await anonyme.dispose();

  expect((await request.post(MARKETING, { data: { nom: 'E2E-sans-csrf', typeCampagne: 'PROMOTION', contenu: 'x' } })).status()).toBe(403);

  const essai = async (nom, contenu) => request.post(MARKETING, { headers: entetes, data: { nom, typeCampagne: 'PROMOTION', canal: 'WHATSAPP', contenu } });
  let r = await essai(`E2E-solde ${suffixe()}`, 'Votre solde : {solde}');
  expect(r.status()).toBe(400);
  expect((await r.json()).message).toContain('{solde}');

  r = await essai(`E2E-inconnue ${suffixe()}`, 'Bonjour {variable_inconnue}');
  expect(r.status()).toBe(400);
  expect((await r.json()).message).toBe('Variable inconnue : {variable_inconnue}.');

  r = await essai('Promo · Lancement', 'Bonjour');
  expect(r.status()).toBe(400);
  expect((await r.json()).message).toBe('Un modèle s\'appelle déjà « Promo · Lancement ».');

  // Une relance ne cite pas de promotion
  r = await request.post(RECOUVREMENT, { headers: entetes,
    data: { nom: `E2E-relance ${suffixe()}`, typeCampagne: 'APRES_ECHEANCE', canal: 'WHATSAPP', contenu: 'Offre {promotion_nom}' } });
  expect(r.status()).toBe(400);
  expect((await r.json()).message).toContain('{promotion_nom}');
});
