// Donnees et comptes de test pour les tableaux de bord, le menu repliable et l'historique (prefixe E2E-).
const { expect, request: fabrique } = require('@playwright/test');
const { jetonCsrf, connexion, suffixe } = require('./aide');

const MOT_DE_PASSE_COMPTE = 'E2eTest2026x';

/** Date du jour decalee, au format ISO. */
function jour(decalage = 0) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + decalage);
  return d.toISOString().substring(0, 10);
}

/** Ecriture avec le jeton CSRF du moment ; renvoie la reponse. */
async function ecrire(request, methode, url, data) {
  return request[methode](url, { headers: { 'X-XSRF-TOKEN': await jetonCsrf(request) }, data: data ?? {} });
}

/** Ecriture qui doit reussir ; renvoie le corps JSON (null pour 204). */
async function ok(request, methode, url, data) {
  const r = await ecrire(request, methode, url, data);
  expect(r.status(), `${methode.toUpperCase()} ${url} : ${await r.text()}`).toBeLessThan(300);
  return r.status() === 204 ? null : r.json();
}

/** Contexte HTTP connecte sous un autre compte. */
async function sessionDe(baseURL, login, motDePasse) {
  const request = await fabrique.newContext({ baseURL });
  await connexion(request, login, motDePasse);
  return request;
}

/** Compte E2E- avec un role sur un module ; son mot de passe provisoire est deja remplace. */
async function creerCompte(request, baseURL, nom, roleCode, moduleCode, agenceIds) {
  const login = `E2E-${nom}-${suffixe()}`;
  const compte = await ok(request, 'post', 'api/utilisateurs', {
    login, nomComplet: `E2E ${nom}`, habilitations: [{ roleCode, moduleCode }], agenceIds,
  });
  const session = await sessionDe(baseURL, login, compte.motDePasseProvisoire);
  await ok(session, 'post', 'api/auth/mot-de-passe', { actuel: compte.motDePasseProvisoire, nouveau: MOT_DE_PASSE_COMPTE });
  await session.dispose();
  return { login, motDePasse: MOT_DE_PASSE_COMPTE };
}

/** Agence E2E- avec un client et une facture de 100 000 FCFA a echeance dans 5 jours : des chiffres exacts quel que soit le reste de la base. */
async function creerAgenceAvecFacture(request) {
  const s = suffixe().toUpperCase();
  const agence = await ok(request, 'post', 'api/agences', { code: `E2E-${s}`.substring(0, 30), libelle: `E2E agence ${s}` });
  const client = await ok(request, 'post', 'api/clients', {
    code: `E2E-C-${s}`, raisonSociale: `E2E Pharmacie ${s}`, agenceId: agence.id, segmentation: 'P', nomPharmacien: 'Dr E2E',
    numeros: { PRINCIPAL: '07 00 00 00 99' },
  });
  const sousTypes = await (await request.get('api/recouvrement/sous-types')).json();
  const facture = {
    reference: `E2E-F-${s}`, clientId: client.id, sousTypeId: sousTypes[0].id, nature: 'FACTURE',
    dateFacture: jour(-25), dateEcheance: jour(5), montant: 100000, solde: 100000,
  };
  const creee = await ok(request, 'post', 'api/recouvrement/factures', facture);
  return { agence, client, facture: { ...facture, id: creee.id } };
}

async function parametre(request, cle, valeur) {
  await ok(request, 'put', 'api/parametres', [{ cle, valeur }]);
}

module.exports = { jour, ecrire, ok, sessionDe, creerCompte, creerAgenceAvecFacture, parametre };
