// Aides partagées par les tests de bout en bout.
const API = 'api/v1'; // relatif : conserve le contexte /ubisenderpro de baseURL
const LOGIN = process.env.E2E_LOGIN || 'admin';
const MOT_DE_PASSE = process.env.E2E_MOT_DE_PASSE || 'Admin@2026';

/** Ouvre une session et renvoie le jeton Bearer. */
async function connexion(request) {
  const r = await request.post(API + '/auth/login', {
    data: { login: LOGIN, motDePasse: MOT_DE_PASSE },
  });
  if (r.status() !== 200) {
    throw new Error('Connexion impossible (' + r.status() + ') : vérifiez E2E_LOGIN / E2E_MOT_DE_PASSE');
  }
  return (await r.json()).token;
}

function entetes(token) {
  return { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
}

/** Suffixe unique : les données de test sont reconnaissables et sans collision. */
function suffixe() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}

module.exports = { API, LOGIN, MOT_DE_PASSE, connexion, entetes, suffixe };
