// Niveau 3 - E2E interface : bulles detaillees des boutons, « ? » de chaque ecran et son panneau, reglages du guidage.
const { test, expect } = require('@playwright/test');
const { LOGIN } = require('../aide');
const { donnees, seConnecterEn } = require('../comptes');

let jeu;
test.beforeAll(async ({ baseURL }) => { jeu = await donnees(baseURL); });

test('le survol d un bouton montre sa bulle detaillee, y compris pour un bouton-icone', async ({ page }) => {
  const incidents = await seConnecterEn(page, LOGIN);
  await page.goto('factures');
  await page.getByRole('button', { name: 'Nouvelle facture' }).hover();
  const bulle = page.locator('#aide-bulle');
  await expect(bulle).toBeVisible();
  await expect(bulle).toContainText("Saisir une facture qui n'est pas arrivée par l'import");
  await expect(page.getByRole('button', { name: 'Nouvelle facture' })).toHaveAttribute('aria-describedby', 'aide-bulle');
  await page.mouse.move(5, 5);
  await expect(bulle).toBeHidden();

  // Bouton grise : la bulle dit pourquoi
  await page.goto('parametres');
  await page.getByRole('button', { name: 'Enregistrer' }).hover();
  await expect(bulle).toContainText("Aucun réglage n'a été modifié.");
  expect(incidents).toEqual([]);
});

test('le « ? » de chaque ecran ouvre son panneau d aide, Echap le referme', async ({ page }) => {
  const incidents = await seConnecterEn(page, jeu.comptes.agentYop);
  for (const [chemin, titre, extrait] of [
    ['factures', 'Factures et créances', 'Relancer ces clients'],
    ['ma-journee', 'Ma journée', 'Nouvelle campagne'],
    ['campagnes', 'Campagnes de relance', 'Soumettez']
  ]) {
    await page.goto(chemin);
    const q = page.getByRole('button', { name: 'Aide de cet écran' });
    await expect(q).toHaveCount(1);
    await q.click();
    const panneau = page.locator('#aide-panneau');
    await expect(panneau.getByRole('heading', { name: titre })).toBeVisible();
    await expect(panneau).toContainText(extrait);
    await expect(q).toHaveAttribute('aria-expanded', 'true');
    // Un agent ne modifie pas les textes
    await expect(panneau.getByText('Modifier ce texte')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(panneau).toBeHidden();
  }
  expect(incidents).toEqual([]);
});

test('un utilisateur coupe ses bulles depuis le panneau, et retrouve son choix', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  await page.goto('factures');
  await page.getByRole('button', { name: 'Aide de cet écran' }).click();
  const choix = page.getByLabel('Infobulles détaillées et animations de saisie');
  await expect(choix).toBeChecked();
  await choix.uncheck();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Nouvelle facture' }).hover();
  await page.waitForTimeout(600);
  await expect(page.locator('#aide-bulle')).toHaveCount(0);
  // Le choix tient apres rechargement, puis on le remet
  await page.reload();
  await page.getByRole('button', { name: 'Aide de cet écran' }).click();
  await expect(choix).not.toBeChecked();
  await choix.check();
  await expect(page.locator('html')).toHaveAttribute('data-guidage', 'oui');
});

test('l administrateur reecrit l aide d un ecran depuis le panneau puis la retablit', async ({ page }) => {
  const incidents = await seConnecterEn(page, LOGIN);
  await page.goto('delais-relance');
  await page.getByRole('button', { name: 'Aide de cet écran' }).click();
  const panneau = page.locator('#aide-panneau');
  await panneau.getByText('Modifier ce texte').click();
  await panneau.locator('input[name=titre]').fill('E2E Délais maison');
  await panneau.locator('textarea[name=texte]').fill('Notre règle :\n1. E2E première étape\n2. E2E seconde étape');
  await panneau.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(panneau.getByRole('heading', { name: 'E2E Délais maison' })).toBeVisible();
  await expect(panneau.locator('ol li')).toHaveText(['E2E première étape', 'E2E seconde étape']);
  // Visible apres rechargement, et dans Parametres > Aide et guidage
  await page.goto('parametres');
  await page.getByRole('button', { name: 'Aide et guidage' }).click();
  await page.getByLabel("Chercher un texte d'aide").fill('E2E Délais');
  await expect(page.locator('table.t tbody tr')).toHaveCount(1);
  await page.getByRole('button', { name: "Revenir au texte d'origine" }).click();
  await expect(page.getByText('Aucun texte ne correspond.')).toBeVisible();
  await page.goto('delais-relance');
  await page.getByRole('button', { name: 'Aide de cet écran' }).click();
  await expect(panneau.getByRole('heading', { name: 'Délais de relance' })).toBeVisible();
  expect(incidents).toEqual([]);
});

test('l interrupteur de l entreprise coupe les bulles de tout le monde', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  await page.goto('parametres');
  await page.getByRole('button', { name: 'Aide et guidage' }).click();
  const choix = page.locator('select[id="aide.guidage"]');
  await choix.selectOption('NON');
  await page.getByRole('button', { name: /^Enregistrer/ }).first().click();
  await expect(page.locator('html')).toHaveAttribute('data-guidage', 'non');
  try {
    await page.goto('factures');
    await page.getByRole('button', { name: 'Nouvelle facture' }).hover();
    await page.waitForTimeout(600);
    await expect(page.locator('#aide-bulle')).toHaveCount(0);
    // Le « ? » reste disponible
    await page.getByRole('button', { name: 'Aide de cet écran' }).click();
    await expect(page.getByText("Coupées pour toute l'entreprise")).toBeVisible();
  } finally {
    await page.goto('parametres');
    await page.getByRole('button', { name: 'Aide et guidage' }).click();
    await page.locator('select[id="aide.guidage"]').selectOption('OUI');
    await page.getByRole('button', { name: /^Enregistrer/ }).first().click();
    await expect(page.locator('html')).toHaveAttribute('data-guidage', 'oui');
  }
});
