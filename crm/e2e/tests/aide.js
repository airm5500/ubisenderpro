// Aides partagees par les tests de bout en bout.
const { expect, request: fabrique } = require('@playwright/test');

const LOGIN = process.env.E2E_LOGIN || 'admin';
const MOT_DE_PASSE = process.env.E2E_MOT_DE_PASSE || 'E2E-Test@2026';

/** Jeton CSRF a renvoyer dans l'en-tete X-XSRF-TOKEN de chaque ecriture : la valeur du cookie XSRF-TOKEN, comme le fait Angular. */
async function jetonCsrf(request) {
  const r = await request.get('api/auth/csrf');
  expect(r.status()).toBe(200);
  const cookie = (await request.storageState()).cookies.find(c => c.name === 'XSRF-TOKEN');
  return cookie.value;
}

/** Ouvre une session (cookie) et renvoie les en-tetes des ecritures. */
async function connexion(request, login = LOGIN, motDePasse = MOT_DE_PASSE) {
  const jeton = await jetonCsrf(request);
  const r = await request.post('api/auth/connexion', { headers: { 'X-XSRF-TOKEN': jeton }, data: { login, motDePasse } });
  if (r.status() !== 200) {
    throw new Error(`Connexion de ${login} impossible (${r.status()}) : verifiez E2E_LOGIN / E2E_MOT_DE_PASSE`);
  }
  // Le jeton change a la connexion
  return { 'X-XSRF-TOKEN': await jetonCsrf(request) };
}

/** Contexte HTTP connecte, pour preparer des donnees depuis un test d'interface. */
async function contexteConnecte(baseURL) {
  const request = await fabrique.newContext({ baseURL });
  const entetes = await connexion(request);
  return { request, entetes };
}

/** Connexion par l'ecran, puis attente du menu. */
async function seConnecter(page) {
  await page.goto('connexion');
  await page.getByRole('textbox', { name: 'Identifiant' }).fill(LOGIN);
  await page.getByLabel('Mot de passe', { exact: true }).fill(MOT_DE_PASSE);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).not.toHaveURL(/connexion/);
}

/** Suffixe unique : les donnees de test sont reconnaissables et sans collision. */
function suffixe() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}

module.exports = { LOGIN, MOT_DE_PASSE, jetonCsrf, connexion, contexteConnecte, seConnecter, suffixe };
