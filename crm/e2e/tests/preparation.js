// Avant la suite : sur une base neuve, le compte de test porte encore le mot de passe
// initial provisoire ; il est remplace par E2E_MOT_DE_PASSE pour que les tests travaillent.
const { request: fabrique } = require('@playwright/test');
const { LOGIN, MOT_DE_PASSE, jetonCsrf } = require('./aide');

const INITIAL = process.env.E2E_MOT_DE_PASSE_INITIAL || 'Admin@2026';

module.exports = async config => {
  const request = await fabrique.newContext({ baseURL: config.projects[0].use.baseURL });
  try {
    const essai = async motDePasse => request.post('api/auth/connexion',
        { headers: { 'X-XSRF-TOKEN': await jetonCsrf(request) }, data: { login: LOGIN, motDePasse } });
    let r = await essai(MOT_DE_PASSE);
    if (r.status() === 200 && !(await r.json()).motDePasseProvisoire) {
      return;
    }
    r = await essai(INITIAL);
    if (r.status() !== 200) {
      throw new Error(`Connexion de ${LOGIN} impossible avec E2E_MOT_DE_PASSE ni avec le mot de passe initial`);
    }
    const c = await request.post('api/auth/mot-de-passe',
        { headers: { 'X-XSRF-TOKEN': await jetonCsrf(request) }, data: { actuel: INITIAL, nouveau: MOT_DE_PASSE } });
    if (c.status() !== 204) {
      throw new Error(`Changement du mot de passe initial refuse (${c.status()}) : ${await c.text()}`);
    }
  } finally {
    await request.dispose();
  }
};
