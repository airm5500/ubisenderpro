// Niveau 5 - UI/UX et controles de saisie : reglage du menu repliable, clavier, accessibilite, periode invalide.
const { test, expect } = require('@playwright/test');
const { contexteConnecte, seConnecter } = require('../aide');
const { jour, ecrire, parametre } = require('../tableaux-donnees');

test.describe.configure({ mode: 'serial' });

let admin;

test.beforeAll(async ({}, info) => { ({ request: admin } = await contexteConnecte(info.project.use.baseURL)); });
test.afterAll(async () => {
  if (admin) {
    await parametre(admin, 'charte.menu_repliable', 'NON');
    await admin.dispose();
  }
});

test('le reglage du menu repliable refuse une valeur autre que OUI ou NON, avec un message qui le nomme', async () => {
  const modifier = valeur => ecrire(admin, 'put', 'api/parametres', [{ cle: 'charte.menu_repliable', valeur }]);
  const r = await modifier('PEUT-ETRE');
  expect(r.status()).toBe(400);
  expect((await r.json()).message).toBe(
    '« Sections du menu de gauche repliables : un clic sur un titre ouvre sa section et ferme celle qui était ouverte » : OUI ou NON.');
  const vide = await modifier('');
  expect(vide.status()).toBe(400);
  expect((await vide.json()).message).toContain('ne peut pas rester vide');
  expect((await modifier('oui')).status()).toBe(400);
  // Les espaces autour de la saisie sont retires
  expect((await modifier(' NON ')).status()).toBe(200);
});

test("le reglage se trouve dans Parametres > Charte et s'applique sans recharger", async ({ page }) => {
  await seConnecter(page);
  await page.goto('parametres');
  await page.locator('button.it', { hasText: /Charte/ }).click();
  const choix = page.locator('[id="charte.menu_repliable"]');
  await expect(choix).toHaveValue('NON');
  await expect(page.locator('.param', { has: choix })).toContainText('Sections du menu de gauche repliables');
  await expect(choix.locator('option')).toHaveText(['Oui', 'Non']);
  await choix.selectOption('OUI');
  await page.getByRole('button', { name: /^Enregistrer/ }).click();
  await expect(page.locator('nav .chevron').first()).toBeVisible();
  await choix.selectOption('NON');
  await page.getByRole('button', { name: /^Enregistrer/ }).click();
  await expect(page.locator('nav .chevron').first()).toBeHidden();
});

test('le menu repliable se manie au clavier et une section fermee ne retient pas le focus', async ({ page }) => {
  await parametre(admin, 'charte.menu_repliable', 'OUI');
  await seConnecter(page);
  await page.goto('historique-envois');
  const administration = page.locator('nav button.gl', { hasText: 'Administration' });
  await administration.focus();
  await page.keyboard.press('Enter');
  await expect(administration).toHaveAttribute('aria-expanded', 'true');
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus')).toHaveText('Utilisateurs et habilitations');
  // Les liens d'une section fermee sont hors du parcours au clavier
  await expect(page.locator('nav .section.fermee .liens').first()).toHaveAttribute('inert', '');
  await administration.focus();
  await page.keyboard.press('Space');
  await expect(administration).toHaveAttribute('aria-expanded', 'false');
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus')).not.toHaveText('Utilisateurs et habilitations');
});

test("l'oeil de l'historique est nomme, s'ouvre au clavier et se ferme a la touche Echap", async ({ page }) => {
  await seConnecter(page);
  await page.goto('historique-envois');
  await page.locator('tbody tr').first().waitFor();
  const oeil = page.getByRole('button', { name: 'Voir le message' }).first();
  test.skip(await oeil.count() === 0, 'Aucun message envoyé dans cet environnement de test.');
  await oeil.hover();
  await expect(page.locator('.p-tooltip')).toHaveText('Voir le message');
  await oeil.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('tbody td div[style*="pre-wrap"]')).toHaveCount(0);
});

test('une periode inversee dans le tableau de bord est signalee, le calendrier reste utilisable', async ({ page }) => {
  await seConnecter(page);
  await page.goto('tableau-de-bord');
  await page.locator('input[name="du"]').fill(jour(5));
  await page.locator('input[name="au"]').fill(jour(0));
  await page.getByRole('button', { name: 'Actualiser' }).click();
  await expect(page.locator('.avert')).toContainText('Le début de la période doit précéder sa fin.');
  await page.getByRole('button', { name: 'Calendrier' }).click();
  const fenetre = page.getByRole('dialog', { name: "Calendrier d'activité" });
  await expect(fenetre.locator('.cj').first()).toBeVisible();
  expect(await fenetre.locator('.cj').count() % 7).toBe(0);
  await expect(fenetre.locator('.barre .discret')).toHaveText('');
  // Chaque case annonce sa date et son nombre d'activites
  await expect(fenetre.locator('.cj.auj')).toHaveAttribute('aria-label', new RegExp(`^${jour(0).split('-').reverse().join('/')} : \\d+ activité`));
  await page.keyboard.press('Escape');
  await expect(fenetre).toBeHidden();
});
