// Niveau 3 - E2E interface : visualisation d'un message de l'historique, calendrier d'activite et menu repliable.
const { test, expect } = require('@playwright/test');
const { contexteConnecte, seConnecter, suffixe } = require('../aide');
const { jour, ok, creerAgenceAvecFacture, parametre } = require('../tableaux-donnees');

test.describe.configure({ mode: 'serial' });

let admin;
let donnees;
const salon = `E2E Salon ${suffixe()}`;
const date = iso => iso.split('-').reverse().join('/');

test.beforeAll(async ({}, info) => {
  ({ request: admin } = await contexteConnecte(info.project.use.baseURL));
  donnees = await creerAgenceAvecFacture(admin);
  await ok(admin, 'put', `api/recouvrement/factures/${donnees.facture.id}`, { ...donnees.facture, solde: 40000 });
  await ok(admin, 'post', 'api/marketing/evenements', { nom: salon, nature: 'AUTRE', dateDebut: jour(1) });
});

test.afterAll(async () => {
  if (admin) {
    await parametre(admin, 'charte.menu_repliable', 'NON');
    await admin.dispose();
  }
});

test("l'oeil de l'historique ouvre le message en entier", async ({ page }) => {
  await seConnecter(page);
  await page.goto('historique-envois');
  await page.locator('tbody tr').first().waitFor();
  const oeil = page.getByRole('button', { name: 'Voir le message' }).first();
  test.skip(await oeil.count() === 0, 'Aucun message envoyé dans cet environnement de test : rien à visualiser.');
  const ligne = page.locator('tbody tr').filter({ has: oeil }).first();
  const extrait = (await ligne.locator('td').nth(5).innerText()).trim().replace(/…$/, '').substring(0, 30);
  await oeil.click();
  const fenetre = page.getByRole('dialog');
  await expect(fenetre).toBeVisible();
  await expect(fenetre.locator('.fil-wa, .fil-sms, .courriel')).toContainText(extrait);
  // Le clic sur l'oeil ne deplie pas la ligne
  await expect(ligne.locator('td').nth(5).locator('div')).toHaveClass(/second/);
  await page.keyboard.press('Escape');
  await expect(fenetre).toBeHidden();
});

test('le calendrier du recouvrement retrace la journee et ramene le tableau de bord sur ce jour', async ({ page }) => {
  await seConnecter(page);
  await page.goto('tableau-de-bord');
  await page.getByLabel('Agence').selectOption({ label: donnees.agence.libelle });
  await page.getByRole('button', { name: 'Calendrier' }).click();
  const fenetre = page.getByRole('dialog', { name: "Calendrier d'activité" });
  await expect(fenetre).toBeVisible();
  const mois = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date());
  await expect(fenetre.locator('.mois')).toHaveText(new RegExp(mois, 'i'));
  const aujourdhui = fenetre.locator('.cj.auj');
  await expect(aujourdhui.locator('.pastille')).toContainText('60');
  await aujourdhui.click();
  await expect(fenetre.locator('.detail')).toContainText('Encaissé sur 1 facture');
  await expect(fenetre.locator('.detail')).toContainText('60 000 FCFA');
  const echeance = fenetre.locator(`.cj[aria-label^="${date(jour(5))} :"]`);
  if (jour(5).substring(0, 7) === jour(0).substring(0, 7)) {
    await expect(echeance.locator('.pastille[title="1 facture à échéance"]')).toHaveCount(1);
  }
  await fenetre.getByRole('button', { name: 'Mois suivant' }).click();
  await expect(fenetre.locator('.mois')).not.toHaveText(new RegExp(mois, 'i'));
  await fenetre.getByRole('button', { name: "Aujourd'hui" }).click();
  await expect(fenetre.locator('.mois')).toHaveText(new RegExp(mois, 'i'));
  await fenetre.getByRole('button', { name: 'Voir ce jour dans le tableau de bord' }).click();
  await expect(fenetre).toBeHidden();
  await expect(page.locator('input[name="du"]')).toHaveValue(jour(0));
  await expect(page.locator('input[name="au"]')).toHaveValue(jour(0));
  await expect(page.locator('.sous-titre')).toContainText('60 000 FCFA encaissés');
});

test('le calendrier du marketing montre les evenements et mene a leur ecran', async ({ page }) => {
  await seConnecter(page);
  await page.goto('marketing/tableau-de-bord');
  await page.getByRole('button', { name: 'Calendrier' }).click();
  const fenetre = page.getByRole('dialog', { name: "Calendrier d'activité Marketing" });
  if (jour(1).substring(0, 7) !== jour(0).substring(0, 7)) {
    await fenetre.getByRole('button', { name: 'Mois suivant' }).click();
  }
  // La case du jour (ses pastilles peuvent etre resumees en « + N » au-dela de trois activites)
  await fenetre.locator(`.cj[aria-label^="${date(jour(1))} :"]`).click();
  await fenetre.locator('.detail .ligne', { hasText: salon }).getByRole('link', { name: 'Ouvrir' }).click();
  await expect(page).toHaveURL(/\/marketing\/evenements$/);
});

test("le menu repliable n'ouvre qu'une section a la fois quand il est active", async ({ page }) => {
  await parametre(admin, 'charte.menu_repliable', 'OUI');
  await seConnecter(page);
  await page.goto('historique-envois');
  const titre = nom => page.locator('nav button.gl', { hasText: nom });
  // La section de l'ecran affiche est ouverte, les autres fermees
  await expect(titre('Échanges')).toHaveAttribute('aria-expanded', 'true');
  await expect(titre('Administration')).toHaveAttribute('aria-expanded', 'false');
  await expect(page.getByRole('link', { name: 'Journal des actions' })).toBeHidden();
  await titre('Administration').click();
  await expect(titre('Administration')).toHaveAttribute('aria-expanded', 'true');
  await expect(titre('Échanges')).toHaveAttribute('aria-expanded', 'false');
  await expect(page.getByRole('link', { name: 'Historique des envois' })).toBeHidden();
  await page.getByRole('link', { name: 'Journal des actions' }).click();
  await expect(page).toHaveURL(/\/journal$/);
  await expect(titre('Administration')).toHaveAttribute('aria-expanded', 'true');
  // Second clic : la section se referme
  await titre('Administration').click();
  await expect(titre('Administration')).toHaveAttribute('aria-expanded', 'false');
});

test('le menu reste entierement deplie par defaut', async ({ page }) => {
  await parametre(admin, 'charte.menu_repliable', 'NON');
  await seConnecter(page);
  await page.goto('historique-envois');
  await expect(page.locator('nav .chevron').first()).toBeHidden();
  await expect(page.locator('nav button.gl').first()).not.toHaveAttribute('aria-expanded');
  await page.locator('nav button.gl', { hasText: 'Administration' }).click();
  for (const lien of ['Historique des envois', 'Journal des actions', 'Factures', 'Centre de support']) {
    await expect(page.getByRole('link', { name: lien })).toBeVisible();
  }
});

test('survoler un lien du menu ne fait jamais apparaitre d ascenseur horizontal', async ({ page }) => {
  await seConnecter(page);
  for (const module of ['Marketing', 'Recouvrement']) {
    await page.locator('nav a.md', { hasText: module }).click();
    const liens = page.locator('.menu-defile a.nv');
    for (let i = 0; i < await liens.count(); i++) {
      const lien = liens.nth(i);
      if (!(await lien.isVisible())) continue;
      await lien.hover();
      await page.waitForTimeout(200);
      // Le survol grossit le lien : s'il deborde, le menu ne doit pas le faire defiler en largeur (le navigateur sans ecran
      // de Playwright masque les ascenseurs, on verifie donc que le debordement est coupe plutot que defilable)
      const etat = await page.locator('.menu-defile').evaluate(m => ({ deborde: m.scrollWidth > m.clientWidth, x: getComputedStyle(m).overflowX }));
      if (etat.deborde) expect(['hidden', 'clip'], await lien.innerText()).toContain(etat.x);
    }
  }
});

test('le choix du module reste fixe, et une fleche signale la suite du menu jusqu en bas', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 620 });
  await seConnecter(page);
  const modules = page.locator('nav .modules');
  const menu = page.locator('.menu-defile');
  const fleche = page.getByRole('button', { name: 'Voir la suite du menu' });
  // Le selecteur n'est plus dans la partie qui defile
  await expect(modules).toBeVisible();
  expect(await menu.locator('.modules').count()).toBe(0);
  const avant = await modules.boundingBox();
  // Aucune barre visible : la largeur utile du menu est toute sa largeur
  expect(await menu.evaluate(m => getComputedStyle(m).scrollbarWidth)).toBe('none');
  await expect(fleche).toHaveCSS('opacity', '1');
  // La fleche rebondit en continu : on la clique sans attendre qu'elle soit immobile
  await fleche.click({ force: true });
  await page.waitForTimeout(600);
  expect(await menu.evaluate(m => m.scrollTop)).toBeGreaterThan(0);
  expect((await modules.boundingBox()).y).toBe(avant.y);
  await menu.evaluate(m => m.scrollTo({ top: m.scrollHeight }));
  await expect(fleche).toHaveCSS('opacity', '0');
  // Les donnees de la partie droite gardent une barre fine et verte (style A)
  expect(await page.locator('main').evaluate(m => getComputedStyle(m).scrollbarWidth)).toBe('thin');
  expect(await page.locator('main').evaluate(m => getComputedStyle(m).scrollbarColor)).toContain('rgb(81, 215, 142)');
});
