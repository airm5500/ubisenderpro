// Assistant d'import : détection des colonnes, y compris l'encodage ANSI
// (windows-1252) des exports Excel français — non-régression du bug « Num�ro ».
const { test, expect } = require('@playwright/test');
const { API, connexion, entetes } = require('../aide');

function b64(buffer) { return Buffer.from(buffer).toString('base64'); }

test.describe('Détection des colonnes (assistant d\'import)', () => {
  let h;
  test.beforeAll(async ({ request }) => { h = entetes(await connexion(request)); });

  test('CSV UTF-8 : colonnes, exemples et total', async ({ request }) => {
    const csv = 'code;nom;telephone\nC001;PHCIE POLAP;2250700000000\nC002;PHCIE PLATEAU;2250700000001\n';
    const r = await request.post(API + '/imports/colonnes', {
      headers: h, data: { fichierBase64: b64(csv), nomFichier: 'clients.csv', separateur: ';' },
    });
    expect(r.status()).toBe(200);
    const a = await r.json();
    expect(a.colonnes).toEqual(['code', 'nom', 'telephone']);
    expect(a.totalLignes).toBe(2);
    expect(a.exemples[0].nom).toBe('PHCIE POLAP');
  });

  test('CSV ANSI (windows-1252) : les accents survivent', async ({ request }) => {
    // « Numéro client;Modifié le » encodé windows-1252 : é = 0xE9.
    const octets = Buffer.from('Num\xe9ro client;Modifi\xe9 le\nC001;2026-01-01\n', 'latin1');
    const r = await request.post(API + '/imports/colonnes', {
      headers: h, data: { fichierBase64: b64(octets), nomFichier: 'export_excel.csv', separateur: ';' },
    });
    expect(r.status()).toBe(200);
    const a = await r.json();
    expect(a.colonnes).toEqual(['Numéro client', 'Modifié le']);
  });

  test('fichier illisible : 400 avec un message clair, pas d\'erreur technique', async ({ request }) => {
    const r = await request.post(API + '/imports/colonnes', {
      headers: h, data: { fichierBase64: b64('nimporte quoi'), nomFichier: 'fichier.xlsx', separateur: ';' },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).erreur).toBeTruthy();
  });
});
