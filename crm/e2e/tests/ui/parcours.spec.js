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
