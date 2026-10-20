// Niveau 5 - UI/UX et controles de saisie : formulaire de modele de message.
const { test, expect } = require('@playwright/test');
const { seConnecter, suffixe } = require('../aide');

test.beforeEach(async ({ page }) => {
  await seConnecter(page);
  await page.goto('marketing/modeles');
  await page.getByRole('button', { name: 'Nouveau modèle' }).click();
});

test('Enregistrer reste grise tant que le nom et le message manquent', async ({ page }) => {
  const enregistrer = page.getByRole('button', { name: 'Enregistrer' });
  await expect(enregistrer).toBeDisabled();
  await page.getByLabel('Nom', { exact: true }).fill(`E2E-vide ${suffixe()}`);
  await expect(enregistrer).toBeDisabled();
  await page.getByLabel('Message', { exact: true }).fill('   ');
  await expect(enregistrer).toBeDisabled();
  await page.getByLabel('Message', { exact: true }).fill('Bonjour');
  await expect(enregistrer).toBeEnabled();
});

test('une variable de facture est refusee dans un modele promotionnel, avec un message clair', async ({ page }) => {
  await page.getByLabel('Nom', { exact: true }).fill(`E2E-solde ${suffixe()}`);
  await page.getByLabel('Message', { exact: true }).fill('Votre solde est de {solde}');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText(/retirez \{solde\}/)).toBeVisible();
});

test('une variable inconnue est signalee', async ({ page }) => {
  await page.getByLabel('Nom', { exact: true }).fill(`E2E-inconnue ${suffixe()}`);
  await page.getByLabel('Message', { exact: true }).fill('Bonjour {pas_une_variable}');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Variable inconnue : {pas_une_variable}.')).toBeVisible();
});

test('le nom d un modele de depart ne peut pas etre repris', async ({ page }) => {
  await page.getByLabel('Nom', { exact: true }).fill('Promo · Lancement');
  await page.getByLabel('Message', { exact: true }).fill('Bonjour');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Un modèle s\'appelle déjà « Promo · Lancement ».')).toBeVisible();
});
