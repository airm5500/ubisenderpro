// Niveau 5 - controles de saisie : connexion, promotion, envoi en masse, client.
const { test, expect } = require('@playwright/test');
const { LOGIN, suffixe } = require('../aide');
const { donnees, seConnecterEn } = require('../comptes');

let jeu;
test.beforeAll(async ({ baseURL }) => { jeu = await donnees(baseURL); });

test('un mauvais mot de passe affiche un message, sans quitter la connexion', async ({ page }) => {
  await page.goto('connexion');
  await page.getByRole('textbox', { name: 'Identifiant' }).fill('admin');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mauvais');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/connexion/);
});

test('promotion : « Créer » grise sans les champs obligatoires, fin avant debut refusee', async ({ page }) => {
  await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('marketing/promotions');
  await page.getByRole('button', { name: 'Nouvelle promotion' }).click();
  const fenetre = page.getByRole('dialog', { name: 'Nouvelle promotion' });
  // La fenetre initialise son formulaire a l'ouverture, puis place le curseur sur le premier champ
  await expect(fenetre.locator('input[name=code]')).toBeFocused();
  const creer = page.getByRole('button', { name: 'Créer la promotion' });
  await expect(creer).toBeDisabled();
  await fenetre.locator('input[name=code]').fill('E2E' + suffixe().slice(-6));
  await expect(creer).toBeDisabled();
  await fenetre.locator('input[name=nom]').fill('E2E dates inversées');
  await fenetre.locator('input[name=dateDebut]').fill('2026-12-10');
  await fenetre.locator('input[name=dateFin]').fill('2026-12-01');
  await creer.click();
  await expect(fenetre.getByText('La date de fin précède la date de début.')).toBeVisible();
  await expect(page).toHaveURL(/marketing\/promotions$/);
});

test('envoi en masse : un numero mal forme est ecarte avec son motif', async ({ page }) => {
  await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('envoi-masse');
  await page.getByRole('button', { name: 'Nouvel envoi' }).click();
  await expect(page.getByRole('button', { name: 'Vérifier les destinataires' })).toBeDisabled();
  await page.fill('input[name=nom]', 'E2E-' + suffixe());
  await page.fill('textarea[name=contenu]', 'Bonjour, test E2E.');
  await page.fill('textarea[name=numeros]', '0799000031;Dr Bon\n12AB;Dr Faux');
  await page.getByRole('button', { name: 'Vérifier les destinataires' }).click();
  await expect(page.getByRole('cell', { name: '12AB' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Envoyer à 1 destinataire/ })).toBeEnabled();
});

test('client : un numero de telephone invalide est refuse avec un message', async ({ page }) => {
  await seConnecterEn(page, LOGIN);
  await page.goto('clients');
  await page.getByRole('button', { name: 'Nouveau client' }).click();
  const fenetre = page.getByRole('dialog', { name: 'Nouveau client' });
  // La fenetre initialise son formulaire a l'ouverture, puis place le curseur sur le premier champ
  await expect(fenetre.locator('input[name=code]')).toBeFocused();
  await fenetre.locator('input[name=code]').fill('E2E-' + suffixe());
  await fenetre.locator('input[name=raisonSociale]').fill('E2E Numéro faux');
  await fenetre.locator('select[name=agenceId]').selectOption({ index: 1 });
  await fenetre.getByLabel('WhatsApp principal (relances)').fill('12');
  await page.getByRole('button', { name: 'Créer le client' }).click();
  await expect(fenetre.getByText('Numéro WhatsApp invalide : 12 (10 chiffres attendus')).toBeVisible();
});
