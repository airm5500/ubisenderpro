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

test('ajout d un produit a une promotion : « aucun produit » quand la recherche ne trouve rien', async ({ page }) => {
  await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('marketing/promotions');
  await page.getByRole('button', { name: 'Nouvelle promotion' }).click();
  const creation = page.getByRole('dialog', { name: 'Nouvelle promotion' });
  await expect(creation.locator('input[name=code]')).toBeFocused();
  await creation.locator('input[name=code]').fill('E2E' + suffixe().slice(-6));
  await creation.locator('input[name=nom]').fill('E2E recherche de produit');
  await creation.locator('input[name=dateDebut]').fill('2026-11-01');
  await creation.locator('input[name=dateFin]').fill('2026-11-30');
  await page.getByRole('button', { name: 'Créer la promotion' }).click();
  const ajout = page.getByRole('dialog', { name: 'Ajouter un produit' });
  await ajout.locator('input[name=recherche]').fill('ZZZ-INTROUVABLE 999');
  await expect(ajout.getByText(/Aucun produit du catalogue ne correspond/)).toBeVisible();
  await expect(ajout.getByRole('button', { name: 'Ajouter à la promotion' })).toBeDisabled();
});

test('un produit absent du catalogue se cree depuis la promotion, et la liste ne decale pas les champs', async ({ page }) => {
  await seConnecterEn(page, jeu.comptes.chargeMarketing);
  await page.goto('marketing/promotions');
  await page.getByRole('button', { name: 'Nouvelle promotion' }).click();
  const creation = page.getByRole('dialog', { name: 'Nouvelle promotion' });
  await expect(creation.locator('input[name=code]')).toBeFocused();
  await creation.locator('input[name=code]').fill('E2E' + suffixe().slice(-6));
  await creation.locator('input[name=nom]').fill('E2E creation rapide de produit');
  await creation.locator('input[name=dateDebut]').fill('2026-11-01');
  await creation.locator('input[name=dateFin]').fill('2026-11-30');
  await page.getByRole('button', { name: 'Créer la promotion' }).click();
  const ajout = page.getByRole('dialog', { name: 'Ajouter un produit' });
  const avantage = ajout.locator('select[name=avantage]');
  const avant = (await avantage.boundingBox()).y;
  const code = 'E2EP' + suffixe().slice(-6);
  await ajout.locator('input[name=recherche]').fill(code);
  await expect(ajout.getByText(/Aucun produit du catalogue ne correspond/)).toBeVisible();
  expect(Math.abs((await avantage.boundingBox()).y - avant)).toBeLessThan(2);
  await ajout.getByRole('button', { name: 'Créer ce produit' }).click();
  await expect(ajout.locator('input[name=nouveauCode]')).toHaveValue(code);
  await ajout.locator('input[name=nouveauLibelle]').fill('E2E Produit cree a la volee');
  await ajout.getByRole('button', { name: 'Créer le produit' }).click();
  await expect(ajout.getByText('E2E Produit cree a la volee')).toBeVisible();
  await ajout.locator('input[name=ugQuantite]').fill('1');
  await expect(ajout.getByRole('button', { name: 'Ajouter à la promotion' })).toBeEnabled();
});
