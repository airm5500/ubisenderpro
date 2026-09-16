// Parcours interface : écran de connexion (Chromium).
const { test, expect } = require('@playwright/test');
const { LOGIN, MOT_DE_PASSE } = require('../aide');

test.describe('Écran de connexion', () => {

  test('la page de connexion s\'affiche', async ({ page }) => {
    // « ./ » : reste dans le contexte /ubisenderpro de baseURL (« / » irait à la racine du serveur).
  await page.goto('./');
    await expect(page.locator('#str_login')).toBeVisible();
    await expect(page.locator('#str_password')).toBeVisible();
    await expect(page.locator('#login')).toBeVisible();
  });

  test('identifiants faux : message d\'erreur, pas d\'accès', async ({ page }) => {
    // « ./ » : reste dans le contexte /ubisenderpro de baseURL (« / » irait à la racine du serveur).
  await page.goto('./');
    await page.fill('#str_login', LOGIN);
    await page.fill('#str_password', 'FAUX-' + Date.now());
    await page.click('#login');
    await expect(page.getByText(/Identifiants invalides/i).first()).toBeVisible();
  });

  test('identifiants valides : l\'application s\'ouvre', async ({ page }) => {
    // « ./ » : reste dans le contexte /ubisenderpro de baseURL (« / » irait à la racine du serveur).
  await page.goto('./');
    await page.fill('#str_login', LOGIN);
    await page.fill('#str_password', MOT_DE_PASSE);
    await page.click('#login');
    await expect(page.getByText(/Bienvenu\(e\)/).first()).toBeVisible({ timeout: 20000 });
  });
});
