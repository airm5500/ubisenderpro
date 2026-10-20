// Tests de bout en bout du CRM, contre une instance DEMARREE de l'application,
// jamais contre la production : ils creent des donnees prefixees E2E-.
//
//   E2E_BASE_URL     : racine de l'application (defaut http://localhost:8080/)
//   E2E_LOGIN        : compte de test (defaut admin)
//   E2E_MOT_DE_PASSE : son mot de passe de test (defaut E2E-Test@2026). Sur une base
//                      neuve, le mot de passe initial Admin@2026 est remplace par celui-ci.
//   E2E_CHROMIUM     : Chromium deja installe sur la machine (facultatif)
//
// Trois projets, un par niveau de la strategie de tests :
//   api     (niveau 2) : routes REST, droits, codes d'erreur
//   ui      (niveau 3) : parcours dans l'interface
//   saisies (niveau 5) : messages d'erreur, champs obligatoires, formats
const { defineConfig } = require('@playwright/test');

const BASE = (process.env.E2E_BASE_URL || 'http://localhost:8080/').replace(/\/?$/, '/');
const navigateur = {
  browserName: 'chromium',
  viewport: { width: 1440, height: 900 },
  launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {},
};

module.exports = defineConfig({
  testDir: './tests',
  timeout: 45000,
  expect: { timeout: 10000 },
  // Les tests partagent la base : un seul worker evite les surprises d'ordre
  workers: 1,
  reporter: [['list']],
  globalSetup: require.resolve('./tests/preparation.js'),
  use: { baseURL: BASE, screenshot: 'only-on-failure' },
  projects: [
    { name: 'api', testDir: './tests/api' },
    { name: 'ui', testDir: './tests/ui', use: navigateur },
    { name: 'saisies', testDir: './tests/saisies', use: navigateur },
  ],
});
