// Niveau 2 - E2E API : securite (session, CSRF, droits, cloisonnement), envoi en masse et « Envoyer maintenant ».
const { test, expect } = require('@playwright/test');
const { suffixe } = require('../aide');
const { Session, json, donnees } = require('../comptes');

let jeu;
test.beforeAll(async ({ baseURL }) => { jeu = await donnees(baseURL); });

test.describe('session et CSRF', () => {
  test('sans session, les API repondent 401', async ({ baseURL }) => {
    const s = await Session.anonyme(baseURL);
    expect((await s.get('api/auth/moi')).status()).toBe(401);
    expect((await s.get('api/clients')).status()).toBe(401);
    await s.fermer();
  });

  test('un mauvais mot de passe recoit le meme message que un compte inconnu', async ({ baseURL }) => {
    const s = await Session.anonyme(baseURL);
    const faux = await s.post('api/auth/connexion', { login: 'admin', motDePasse: 'faux' });
    const inconnu = await s.post('api/auth/connexion', { login: 'personne-e2e', motDePasse: 'faux' });
    expect(faux.status()).toBe(401);
    expect(inconnu.status()).toBe(401);
    expect((await inconnu.json()).message).toBe((await faux.json()).message);
    await s.fermer();
  });

  test('une ecriture sans jeton CSRF est refusee', async ({ baseURL }) => {
    const s = await Session.de(baseURL, 'admin');
    expect((await s.postSansJeton('api/clients', { code: 'E2E-CSRF' })).status()).toBe(403);
    await s.fermer();
  });
});

test.describe('droits par profil et cloisonnement par agence', () => {
  test('un agent ne voit que les clients de son portefeuille, jamais ceux d une autre agence', async ({ baseURL }) => {
    const agent = await Session.de(baseURL, jeu.comptes.agentYop);
    const codes = (await json(await agent.get('api/clients', { q: 'E2E-', taille: 50 }))).elements.map(c => c.code);
    // E2E-YOP est de son agence mais pas de son portefeuille (decision du 22/10/2026)
    expect(codes).not.toContain('E2E-YOP');
    expect(codes).not.toContain('E2E-VRI');
    expect((await agent.get(`api/clients/${jeu.clients.vri}`)).status()).toBe(404);
    expect((await agent.get(`api/clients/${jeu.clients.yop}`)).status()).toBe(404);
    await agent.fermer();
  });

  test('un agent n accede pas a l administration', async ({ baseURL }) => {
    const agent = await Session.de(baseURL, jeu.comptes.agentYop);
    for (const chemin of ['api/utilisateurs', 'api/parametres', 'api/roles']) {
      expect((await agent.get(chemin)).status(), chemin).toBe(403);
    }
    await agent.fermer();
  });

  test('un charge marketing ne touche pas aux factures du recouvrement', async ({ baseURL }) => {
    const charge = await Session.de(baseURL, jeu.comptes.chargeMarketing);
    expect((await charge.get('api/recouvrement/factures')).status()).toBe(403);
    await charge.fermer();
  });

  test('une ressource absente repond 404, une saisie invalide 400 sans trace technique', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const absent = await admin.get('api/clients/999999999');
    expect(absent.status()).toBe(404);
    expect((await absent.json()).message).toBeTruthy();
    const invalide = await admin.post('api/clients', { code: '', raisonSociale: '' });
    expect(invalide.status()).toBe(400);
    expect(await invalide.text()).not.toMatch(/Exception|at ci\.ubipharm|SQL/);
    await admin.fermer();
  });
});

test.describe('envoi en masse', () => {
  test('references, lecture d un fichier et pieces jointes sont controlees', async ({ baseURL }) => {
    const charge = await Session.de(baseURL, jeu.comptes.chargeMarketing);
    const refs = await json(await charge.get('api/envois-masse/references'));
    expect(refs.variables.map(v => v.code)).toContain('nom_pharmacie');

    const lu = await json(await charge.fichier('api/envois-masse/lecture-fichier', 'dest.csv', 'text/csv',
      Buffer.from('Téléphone;Nom\n0799000009;Dr E2E\nabc;Dr Faux\n')));
    expect(lu.valides[0].numero).toBe('2250799000009');
    expect(lu.invalides[0].saisi).toBe('abc');

    expect((await charge.fichier('api/envois-masse/pieces-jointes', 'x.exe', 'application/octet-stream', Buffer.from('MZ'))).status()).toBe(400);
    const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10, 0, 0]);
    expect((await json(await charge.fichier('api/envois-masse/pieces-jointes', 'e2e.png', 'image/png', png))).id).toBeTruthy();
    await charge.fermer();
  });

  test('« Envoyer maintenant » : reserve a l auteur, motif si le numero n est pas relie, sans effet hors attente', async ({ baseURL }) => {
    const charge = await Session.de(baseURL, jeu.comptes.chargeMarketing);
    const envoi = (await json(await charge.post('api/envois-masse', {
      nom: 'E2E-' + suffixe(), contenu: 'Bonjour {nom}, test E2E.', numeros: '0799000011;Dr E2E Un',
    }))).envoi.id;
    const ligne = (await json(await charge.get(`api/envois-masse/${envoi}/destinataires`, { page: 0, taille: 10 }))).elements[0];
    expect(ligne.statut).toBe('A_ENVOYER');

    const agent = await Session.de(baseURL, jeu.comptes.agentYop);
    expect([400, 403, 404]).toContain((await agent.post(`api/envois/S${ligne.id}/immediat`)).status());
    await agent.fermer();

    // L'environnement de test n'a pas de telephone relie : l'auteur recoit le motif, rien ne part
    const r = await charge.post(`api/envois/S${ligne.id}/immediat`);
    expect(r.status()).toBe(400);
    expect((await r.json()).message).toContain('pas connecté');
    expect((await charge.post('api/envois/X1/immediat')).status()).toBe(404);

    await json(await charge.post(`api/envois-masse/${envoi}/annulation`));
    expect((await charge.post(`api/envois/S${ligne.id}/immediat`)).status()).toBe(400);
    await charge.fermer();
  });
});

test.describe('charte et promotions', () => {
  test('la banniere refuse le SVG', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const r = await admin.fichier('api/charte/banniere', 'b.svg', 'image/svg+xml', Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>'));
    expect(r.status()).toBe(400);
    await admin.fermer();
  });

  test('une promotion dont la fin precede le debut est refusee', async ({ baseURL }) => {
    const charge = await Session.de(baseURL, jeu.comptes.chargeMarketing);
    const r = await charge.post('api/marketing/promotions', { code: 'E2E' + suffixe(), nom: 'E2E dates', dateDebut: '2026-12-10', dateFin: '2026-12-01' });
    expect(r.status()).toBe(400);
    await charge.fermer();
  });
});

test.describe('discussions', () => {
  test('le reglage « directeur : seulement son module » existe, desactive par defaut', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const reglage = (await json(await admin.get('api/parametres'))).find(p => p.cle === 'discussions.directeur_module_seul');
    expect(reglage).toMatchObject({ valeur: 'NON', typeValeur: 'OUI_NON', categorie: 'WHATSAPP' });
    await admin.fermer();
  });
});

test.describe('campagnes Marketing et reponses des clients', () => {
  test('le reglage « directeur d agence : toutes agences » existe, desactive par defaut', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const reglage = (await json(await admin.get('api/parametres'))).find(p => p.cle === 'marketing.validation_directeur_agence_toutes_agences');
    expect(reglage).toMatchObject({ valeur: 'NON', typeValeur: 'OUI_NON', categorie: 'MARKETING' });
    await admin.fermer();
  });

  test('les reponses se lisent une ligne par client, et le fil dans l ordre chronologique', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const parClient = await json(await admin.get('api/recouvrement/reponses/par-client'));
    expect(parClient).toMatchObject({ page: 0, taille: 50 });
    const cles = parClient.elements.map(r => r.clientId ? 'c' + r.clientId : 'n' + r.numeroClient);
    expect(new Set(cles).size).toBe(cles.length);
    for (const r of parClient.elements) expect(r.nbReponses).toBeGreaterThan(0);
    const fil = await json(await admin.get('api/recouvrement/reponses?chronologique=true&taille=200'));
    const dates = fil.elements.map(r => r.recuLe);
    expect(dates).toEqual([...dates].sort());
    await admin.fermer();
  });
});

test.describe('nom de l entreprise et compte Meta', () => {
  test('{nom_entreprise} est une variable et la charte publique donne le nom', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const variables = await json(await admin.get('api/recouvrement/variables'));
    expect(variables.find(v => v.code === 'nom_entreprise')).toMatchObject({ source: 'calcul.nom_entreprise' });
    const anonyme = await Session.anonyme(baseURL);
    expect(await json(await anonyme.get('api/charte'))).toMatchObject({ nomEntreprise: 'UBIPHARM CI' });
    const modeles = await json(await admin.get('api/recouvrement/modeles-messages'));
    expect(modeles.filter(m => /UBIPHARM/.test(m.contenu))).toEqual([]);
    await anonyme.fermer();
    await admin.fermer();
  });

  test('le jeton Meta ne revient jamais vers l ecran', async ({ baseURL }) => {
    const admin = await Session.de(baseURL, 'admin');
    const compte = await json(await admin.get('api/whatsapp/meta'));
    expect(compte.jetonAcces).toBeUndefined();
    expect(compte.secretApplication).toBeUndefined();
    await admin.fermer();
  });
});
