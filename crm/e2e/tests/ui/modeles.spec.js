// Niveau 3 - E2E interface : consulter, exporter et modifier les modeles de messages.
const { test, expect } = require('@playwright/test');
const { contexteConnecte, seConnecter, suffixe } = require('../aide');

test.beforeEach(async ({ page }) => { await seConnecter(page); });

test('les modeles promotionnels de depart se consultent et s exportent en Word', async ({ page }) => {
  await page.goto('marketing/modeles');
  await expect(page.getByRole('heading', { name: 'Modèles de messages' })).toBeVisible();
  for (const nom of ['Promo · Lancement', 'Dispo · Retour de rupture', 'Info · Rappel de garde', 'Anniversaire client']) {
    await expect(page.getByRole('button', { name: new RegExp(nom) })).toBeVisible();
  }
  await expect(page.getByRole('button', { name: /Relance échue/ })).toHaveCount(0);

  await page.getByRole('button', { name: /Promo · Lancement/ }).click();
  await expect(page.getByText('NOUVELLE PROMOTION UG').first()).toBeVisible();
  const telechargement = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exporter en Word' }).click();
  expect((await telechargement).suggestedFilename()).toMatch(/\.docx$/);
});

test('les courriers de relance de depart sont dans le Recouvrement', async ({ page }) => {
  await page.goto('modeles');
  await expect(page.getByRole('heading', { name: 'Modèles de messages' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Relance préventive/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Promo · Lancement/ })).toHaveCount(0);
  await page.getByRole('button', { name: /Relance échue/ }).click();
  await expect(page.getByText('interruption du traitement de vos commandes').first()).toBeVisible();
});

test('un modele se modifie depuis l ecran', async ({ page, baseURL }) => {
  const nom = `E2E-Anniversaire ${suffixe()}`;
  const { request, entetes } = await contexteConnecte(baseURL);
  const r = await request.post('api/marketing/modeles', { headers: entetes,
    data: { nom, typeCampagne: 'PROMOTION', canal: 'WHATSAPP', contenu: 'Joyeux anniversaire *{nom_client}* !' } });
  expect(r.status()).toBe(200);
  const id = (await r.json()).id;

  await page.goto('marketing/modeles');
  await page.getByRole('button', { name: new RegExp(nom) }).click();
  await page.getByRole('button', { name: 'Modifier' }).click();
  await page.getByLabel('Message', { exact: true }).fill('Bon anniversaire *{nom_client}*, de la part de toute l\'équipe.');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Modèle enregistré')).toBeVisible();
  await expect(page.getByText('de la part de toute l\'équipe').first()).toBeVisible();

  await request.post(`api/marketing/modeles/${id}/archivage`, { headers: entetes });
  await request.dispose();
});
