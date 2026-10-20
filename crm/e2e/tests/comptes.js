// Comptes et clients E2E- d'agences differentes, pour les controles de droits et de cloisonnement.
// Crees a la demande (idempotent) par le compte de test, avec le meme mot de passe que lui.
const { expect, request: fabrique } = require('@playwright/test');
const { MOT_DE_PASSE, connexion, jetonCsrf } = require('./aide');

/** Client HTTP connecte : en-tete CSRF repris du cookie sur chaque ecriture. */
class Session {
  constructor(request) { this.request = request; }

  static async anonyme(baseURL) {
    const request = await fabrique.newContext({ baseURL });
    await jetonCsrf(request);
    return new Session(request);
  }

  static async de(baseURL, login, motDePasse = MOT_DE_PASSE) {
    const request = await fabrique.newContext({ baseURL });
    await connexion(request, login, motDePasse);
    return new Session(request);
  }

  async entetes() {
    const cookie = (await this.request.storageState()).cookies.find(c => c.name === 'XSRF-TOKEN');
    return cookie ? { 'X-XSRF-TOKEN': cookie.value } : {};
  }
  get(chemin, params) { return this.request.get(chemin, { params }); }
  async post(chemin, data = {}) { return this.request.post(chemin, { data, headers: await this.entetes() }); }
  async fichier(chemin, name, mimeType, buffer) {
    return this.request.post(chemin, { multipart: { fichier: { name, mimeType, buffer } }, headers: await this.entetes() });
  }
  /** Sans jeton CSRF : doit etre refuse. */
  postSansJeton(chemin, data = {}) { return this.request.post(chemin, { data }); }
  fermer() { return this.request.dispose(); }
}

/** Corps JSON d'une reponse dont le code est verifie. */
async function json(r, code = 200) {
  expect(r.status(), await r.text()).toBe(code);
  return code === 204 ? undefined : r.json();
}

const COMPTES = {
  agentYop: { login: 'e2e.agent', nomComplet: 'E2E Agent Yopougon', roleCode: 'AGENT', moduleCode: 'RECOUVREMENT', agence: 1, numero: '07 98 00 00 01' },
  chargeMarketing: { login: 'e2e.marketing', nomComplet: 'E2E Chargé marketing', roleCode: 'CHARGE_MARKETING', moduleCode: 'MARKETING', agence: 1, numero: '07 98 00 00 02' },
};
const CLIENTS = {
  yop: { code: 'E2E-YOP', raisonSociale: 'E2E Pharmacie Yopougon', agenceId: 1, numero: '07 99 00 00 01' },
  vri: { code: 'E2E-VRI', raisonSociale: 'E2E Pharmacie Vridi', agenceId: 2, numero: '07 99 00 00 02' },
};

/** Cree s'il le faut les comptes et clients E2E-, puis renvoie leurs logins et ids. */
async function donnees(baseURL) {
  const admin = await Session.de(baseURL, process.env.E2E_LOGIN || 'admin');
  try {
    const clients = {};
    for (const [cle, c] of Object.entries(CLIENTS)) {
      const trouves = (await json(await admin.get('api/clients', { q: c.code, taille: 5 }))).elements.filter(x => x.code === c.code);
      clients[cle] = trouves.length ? trouves[0].id : (await json(await admin.post('api/clients', {
        code: c.code, raisonSociale: c.raisonSociale, agenceId: c.agenceId, segmentation: 'P', nomPharmacien: 'Dr ' + c.code,
        numeros: { PRINCIPAL: c.numero },
      }))).id;
    }
    const comptes = {};
    for (const [cle, u] of Object.entries(COMPTES)) {
      const essai = await Session.anonyme(baseURL);
      const r = await essai.post('api/auth/connexion', { login: u.login, motDePasse: MOT_DE_PASSE });
      await essai.fermer();
      if (r.status() !== 200) {
        const cree = await json(await admin.post('api/utilisateurs', {
          login: u.login, nomComplet: u.nomComplet, numeroWhatsapp: u.numero,
          habilitations: [{ roleCode: u.roleCode, moduleCode: u.moduleCode }], agenceIds: [u.agence],
        }));
        const s = await Session.de(baseURL, u.login, cree.motDePasseProvisoire);
        await json(await s.post('api/auth/mot-de-passe', { actuel: cree.motDePasseProvisoire, nouveau: MOT_DE_PASSE }), 204);
        await s.fermer();
      }
      comptes[cle] = u.login;
    }
    return { clients, comptes };
  } finally {
    await admin.fermer();
  }
}

/** Connexion par l'ecran avec un autre compte que celui de test ; renvoie les erreurs JS et reponses 5xx relevees. */
async function seConnecterEn(page, login, motDePasse = MOT_DE_PASSE) {
  const incidents = [];
  page.on('pageerror', e => incidents.push('JS ' + e.message));
  page.on('response', r => { if (r.status() >= 500) incidents.push(r.status() + ' ' + r.url()); });
  await page.goto('connexion');
  await page.getByRole('textbox', { name: 'Identifiant' }).fill(login);
  await page.getByLabel('Mot de passe', { exact: true }).fill(motDePasse);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).not.toHaveURL(/connexion/);
  return incidents;
}

module.exports = { Session, json, donnees, seConnecterEn };
