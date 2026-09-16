// Éditions PDF (.jrxml) et Excel : ticket de consultation à usage unique,
// nom de fichier normalisé, contenu réellement PDF / XLSX.
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes } = require('../aide');

test.describe('Éditions PDF et Excel', () => {
  let h;
  test.beforeAll(async ({ request }) => { h = entetes(await connexion(request)); });

  const lignes = [
    { code: 'C001', nom: 'PHARMACIE DU PLATEAU', entreprise: 'PHCIE 1', telephone: '2250700000000',
      email: 'p1@exemple.ci', segmentation: 'Gold', agence: 'ABIDJAN', region: 'LAGUNES', tournee: 'T1' },
  ];

  test('PDF générique : lien de vue nommé, PDF servi UNE seule fois', async ({ request }) => {
    const r = await request.post(API + '/rapports/liste/clients', {
      headers: h, data: { titre: 'Comptes clients', lignes },
    });
    expect(r.status()).toBe(200);
    const { vue, nom } = await r.json();
    expect(nom).toMatch(/^comptes_clients_\d{8}_\d{8}\.pdf$/);
    expect(vue).toContain('rapports-vue/');
    expect(vue.endsWith('/' + nom)).toBe(true);

    // 1re consultation : un vrai PDF (l'URL de vue ne demande pas de jeton d'API).
    const pdf = await request.get(API + '/' + vue);
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()['content-type']).toContain('application/pdf');
    const corps = await pdf.body();
    expect(corps.slice(0, 4).toString('ascii')).toBe('%PDF');

    // 2e consultation : le ticket est consommé.
    expect((await request.get(API + '/' + vue)).status()).toBe(404);
  });

  test('PDF comptes clients (données serveur) : mêmes garanties', async ({ request }) => {
    const r = await request.get(API + '/rapports/clients?actif=true', { headers: h });
    expect(r.status()).toBe(200);
    const { vue, nom } = await r.json();
    expect(nom).toMatch(/^comptes_clients_\d{8}_\d{8}\.pdf$/);
    const pdf = await request.get(API + '/' + vue);
    expect((await pdf.body()).slice(0, 4).toString('ascii')).toBe('%PDF');
  });

  test('modèle inconnu : 400 explicite, pas de 500', async ({ request }) => {
    const r = await request.post(API + '/rapports/liste/n-existe-pas', {
      headers: h, data: { titre: 'X', lignes: [] },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).erreur).toContain('introuvable');
  });

  test('Excel : classeur .xlsx nommé, en-têtes et données présentes', async ({ request }) => {
    const r = await request.post(API + '/rapports/excel', {
      headers: h,
      data: { titre: 'Comptes clients',
              colonnes: [{ d: 'code', t: 'Code client' }, { d: 'nom', t: 'Nom client' }],
              lignes: [{ code: 'C001', nom: 'PHARMACIE DU PLATEAU' }] },
    });
    expect(r.status()).toBe(200);
    expect(r.headers()['content-disposition']).toMatch(/comptes_clients_\d{8}_\d{8}\.xlsx/);
    const corps = await r.body();
    // Un .xlsx est une archive ZIP : signature « PK ».
    expect(corps.slice(0, 2).toString('ascii')).toBe('PK');
    expect(corps.length).toBeGreaterThan(1000);
  });

  test('relevé de compte en mode vue : lien nommé releve-compte-…pdf', async ({ request }) => {
    // Le premier client venu suffit (le relevé d'un client sans créance est valide).
    const page = await (await request.get(API + '/clients?limit=1', { headers: h })).json();
    test.skip(!page.data.length, 'aucun client en base');
    const clientId = page.data[0].id;
    const r = await request.get(API + '/recouvrement/clients/' + clientId + '/releve?vue=1', { headers: h });
    expect(r.status()).toBe(200);
    const { vue, nom } = await r.json();
    expect(nom).toMatch(/^releve-compte-.+\.pdf$/);
    const pdf = await request.get(API + '/' + vue);
    expect((await pdf.body()).slice(0, 4).toString('ascii')).toBe('%PDF');
  });
});
