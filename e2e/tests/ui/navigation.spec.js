// Parcours interface : navigation dans les menus après connexion.
const { test, expect } = require('@playwright/test');
const { LOGIN, MOT_DE_PASSE } = require('../aide');

async function seConnecter(page) {
  // « ./ » : reste dans le contexte /ubisenderpro de baseURL (« / » irait à la racine du serveur).
  await page.goto('./');
  await page.fill('#str_login', LOGIN);
  await page.fill('#str_password', MOT_DE_PASSE);
  await page.click('#login');
  await expect(page.getByText(/Bienvenu\(e\)/).first()).toBeVisible({ timeout: 20000 });
}

test.describe('Navigation', () => {

  test('Comptes clients : liste, onglets, mise à jour sélective', async ({ page }) => {
    await seConnecter(page);
    await page.getByText('Comptes clients', { exact: false }).first().click();
    await expect(page.getByText(/Liste des Clients/).first()).toBeVisible({ timeout: 20000 });
    // Les onglets clés du menu sont présents.
    await expect(page.getByText(/Listes de diffusion/).first()).toBeVisible();
    await expect(page.getByText(/Mise à jour sélective/).first()).toBeVisible();
    // L'onglet Mise à jour sélective s'ouvre : volet « Nouvelles valeurs » à droite.
    await page.getByText(/Mise à jour sélective/).first().click();
    await expect(page.getByText(/Nouvelles valeurs/).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Aucun compte coché/).first()).toBeVisible();
  });

  test('le bouton Exporter propose Excel et PDF', async ({ page }) => {
    await seConnecter(page);
    await page.getByText('Comptes clients', { exact: false }).first().click();
    await expect(page.getByText(/Liste des Clients/).first()).toBeVisible({ timeout: 20000 });
    await page.getByText(/⬇️ Exporter/).first().click();
    await expect(page.getByText(/Excel \(\.xlsx\)/).first()).toBeVisible();
    await expect(page.getByText(/PDF/).first()).toBeVisible();
  });
});
