// Niveau 3 - E2E interface : navigation dans tous les menus, promotion, envoi en masse et « Envoyer maintenant ».
const { test, expect } = require('@playwright/test');
const { LOGIN, suffixe } = require('../aide');
const { donnees, seConnecterEn } = require('../comptes');

let jeu;
test.beforeAll(async ({ baseURL }) => { jeu = await donnees(baseURL); });

for (const module of ['Recouvrement', 'Marketing']) {
  test(`chaque ecran du menu ${module} s ouvre sans erreur`, async ({ page }) => {
    test.setTimeout(120000);
    const incidents = await seConnecterEn(page, LOGIN);
    await page.locator('nav a.md', { hasText: module }).click();
    const liens = await page.locator('nav a.nv').evaluateAll(as => as.map(a => a.getAttribute('href')));
    expect(liens.length).toBeGreaterThan(5);
    for (const lien of liens) {
      await page.goto(lien.replace(/^\//, ''));
      await expect(page.locator('h1').first(), lien).toBeVisible();
    }
    expect(incidents).toEqual([]);
  });
}

test('apres la creation d une promotion, sa fiche s ouvre sur l ajout du premier produit', async ({ page }) => {
  const incidents = await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('marketing/promotions');
  await page.getByRole('button', { name: 'Nouvelle promotion' }).click();
  const fenetre = page.getByRole('dialog', { name: 'Nouvelle promotion' });
  // La fenetre initialise son formulaire a l'ouverture, puis place le curseur sur le premier champ
  await expect(fenetre.locator('input[name=code]')).toBeFocused();
  await fenetre.locator('input[name=code]').fill('E2E' + suffixe().slice(-6));
  await fenetre.locator('input[name=nom]').fill('E2E promotion de novembre');
  await fenetre.locator('input[name=dateDebut]').fill('2026-11-01');
  await fenetre.locator('input[name=dateFin]').fill('2026-11-30');
  await page.getByRole('button', { name: 'Créer la promotion' }).click();
  await expect(page).toHaveURL(/marketing\/promotions\/\d+/);
  await expect(page.getByRole('dialog', { name: 'Ajouter un produit' })).toBeVisible();
  expect(incidents).toEqual([]);
});

test('un envoi en masse se verifie, part, et chaque ligne en attente a son bouton « Envoyer maintenant »', async ({ page }) => {
  const incidents = await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('envoi-masse');
  await page.getByRole('button', { name: 'Nouvel envoi' }).click();
  const nom = 'E2E-' + suffixe();
  await page.fill('input[name=nom]', nom);
  await page.fill('textarea[name=contenu]', 'Bonjour {nom}, test E2E.');
  await page.fill('textarea[name=numeros]', '0799000021;Dr E2E Deux\n0799000022;Dr E2E Trois');
  await page.getByRole('button', { name: 'Vérifier les destinataires' }).click();
  await page.getByRole('button', { name: /Envoyer à 2 destinataire/ }).click();
  await expect(page.getByRole('heading', { name: nom })).toBeVisible();
  const boutons = page.locator('app-envoyer-maintenant button');
  await expect(boutons).toHaveCount(2);
  await expect(boutons.first()).toBeEnabled();

  await page.goto('historique-envois');
  await expect(page.locator('app-envoyer-maintenant button').first()).toBeVisible();
  expect(incidents).toEqual([]);
});

test('le reglage des discussions du directeur apparait dans Parametres', async ({ page }) => {
  const incidents = await seConnecterEn(page, LOGIN);
  await page.goto('parametres');
  await page.getByText('WhatsApp', { exact: true }).first().click().catch(() => {});
  await expect(page.getByText('Un directeur d\'agence ne voit que les discussions des agents de son module')).toBeVisible();
  expect(incidents).toEqual([]);
});

test('sur un grand ecran (27 pouces), les pages occupent toute la largeur', async ({ page }) => {
  await page.setViewportSize({ width: 2560, height: 1440 });
  await seConnecterEn(page, LOGIN);
  await page.goto('tableau-de-bord');
  const largeur = await page.locator('.page').first().evaluate(e => e.getBoundingClientRect().width);
  const menu = await page.locator('nav').first().evaluate(e => e.getBoundingClientRect().width);
  expect(largeur).toBeGreaterThan(2560 - menu - 40);
});

test('creation d un compte : agences toutes cochees pour une direction, pas d ajout de role au-dela des modules', async ({ page }) => {
  const incidents = await seConnecterEn(page, LOGIN);
  await page.goto('utilisateurs');
  await page.getByRole('button', { name: 'Nouveau compte' }).click();
  const fenetre = page.getByRole('dialog').filter({ hasText: 'Rôles par module' });
  await fenetre.getByLabel('Rôle').first().selectOption('DIR_AGENCE');
  const cases = fenetre.locator('label.case input');
  await expect(cases.first()).toBeChecked();
  const total = await cases.count();
  await expect(fenetre.locator('label.case input:checked')).toHaveCount(total);
  await fenetre.getByRole('button', { name: 'Tout décocher' }).click();
  await expect(fenetre.locator('label.case input:checked')).toHaveCount(0);
  await fenetre.getByRole('button', { name: 'Tout cocher' }).click();
  await expect(fenetre.locator('label.case input:checked')).toHaveCount(total);
  // Un role sur un module laisse ajouter l'autre ; « Tous les modules » couvre tout
  await expect(fenetre.getByRole('button', { name: /Ajouter un rôle sur un autre module/ })).toBeVisible();
  await fenetre.getByLabel('Module').first().selectOption('');
  await expect(fenetre.getByRole('button', { name: /Ajouter un rôle sur un autre module/ })).toHaveCount(0);
  expect(incidents).toEqual([]);
});

test('le bas du menu (se deconnecter) reste visible sans faire defiler', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 560 });
  await seConnecterEn(page, LOGIN);
  await page.goto('factures');
  const sortie = page.getByRole('button', { name: 'Se déconnecter' });
  await expect(sortie).toBeInViewport();
});

test('le Marketing voit les clients de toutes les agences, l ecran Clients se charge', async ({ page }) => {
  const incidents = await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('clients');
  await expect(page.getByRole('heading', { name: 'Clients', exact: true })).toBeVisible();
  await page.getByLabel(/Pharmacie, pharmacien/).fill('E2E-');
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await expect(page.getByText('E2E Pharmacie Yopougon')).toBeVisible();
  await expect(page.getByText('E2E Pharmacie Vridi')).toBeVisible();
  await expect(page.getByText('Chargement…')).toHaveCount(0);
  expect(incidents).toEqual([]);
});

test('un compte du seul Recouvrement n entre pas dans le Marketing', async ({ page }) => {
  await seConnecterEn(page, jeu.comptes.agentYop);
  await page.goto('marketing');
  await expect(page).not.toHaveURL(/\/marketing/);
});

test('Parametres > Charte : rien ne deborde, meme sur un ecran etroit', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  for (const largeur of [1440, 1100]) {
    await page.setViewportSize({ width: largeur, height: 900 });
    await page.goto('parametres');
    await page.getByText('Charte', { exact: true }).first().click();
    await expect(page.getByText('Choisir une bannière')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), `largeur ${largeur}`).toBe(true);
  }
});
