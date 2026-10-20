// Niveau 5 - UI/UX et saisies : retours animes du guidage (champ oublie qui tremble avec son message, coche de reussite).
const { test, expect } = require('@playwright/test');
const { LOGIN } = require('../aide');
const { seConnecterEn } = require('../comptes');

test('appuyer sur un bouton grise signale les champs obligatoires vides', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  await page.goto('agences');
  await page.getByRole('button', { name: 'Nouvelle agence' }).click();
  const fenetre = page.getByRole('dialog');
  const enregistrer = fenetre.getByRole('button', { name: 'Enregistrer' });
  await expect(enregistrer).toBeDisabled();
  const zone = await enregistrer.boundingBox();
  await page.mouse.move(zone.x + zone.width / 2, zone.y + zone.height / 2);
  await page.mouse.down();
  await page.mouse.up();
  const nom = fenetre.locator('input[name=libelle]');
  await expect(nom).toHaveClass(/aide-champ-ko/);
  await expect(nom).toHaveAttribute('aria-invalid', 'true');
  await expect(fenetre.locator('.aide-champ-msg').first()).toHaveText('Ce champ est obligatoire.');
  // La saisie efface le signalement
  await nom.fill('E2E agence');
  await expect(nom).not.toHaveClass(/aide-champ-ko/);
});

test('un champ refuse par le serveur tremble avec son message, une coche salue la reussite', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  await page.goto('modeles');
  await page.getByRole('button', { name: 'Aide de cet écran' }).click();
  const panneau = page.locator('#aide-panneau');
  await panneau.getByText('Modifier ce texte').click();
  const titre = panneau.locator('input[name=titre]');
  await titre.fill('   ');
  await panneau.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(titre).toHaveClass(/aide-champ-ko/);
  await expect(panneau.locator('.aide-champ-msg')).toHaveText('Saisissez le titre.');
  await titre.fill('Modèles de messages');
  await expect(titre).not.toHaveClass(/aide-champ-ko/);
  await panneau.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.locator('.aide-coche')).toBeVisible();
  // Remise du texte d'origine
  await panneau.getByText('Modifier ce texte').click();
  await panneau.getByRole('button', { name: "Revenir au texte d'origine" }).click();
  await expect(panneau.getByText('Les messages types des relances')).toBeVisible();
});

test('sans animation demandee par le poste, le champ est signale sans trembler', async ({ browser, baseURL }) => {
  const contexte = await browser.newContext({ baseURL, reducedMotion: 'reduce', viewport: { width: 1440, height: 900 } });
  const page = await contexte.newPage();
  await seConnecterEn(page, LOGIN);
  await page.goto('agences');
  await page.getByRole('button', { name: 'Nouvelle agence' }).click();
  const enregistrer = page.getByRole('dialog').getByRole('button', { name: 'Enregistrer' });
  const zone = await enregistrer.boundingBox();
  await page.mouse.click(zone.x + zone.width / 2, zone.y + zone.height / 2);
  const nom = page.getByRole('dialog').locator('input[name=libelle]');
  await expect(nom).toHaveClass(/aide-champ-ko/);
  expect(await nom.evaluate(e => getComputedStyle(e).animationName)).toBe('none');
  await contexte.close();
});
