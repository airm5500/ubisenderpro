// Configuration des tests de bout en bout.
//
// La suite s'exécute contre une instance DÉPLOYÉE de l'application (Payara +
// MySQL/MariaDB), désignée par E2E_BASE_URL — jamais contre la production :
// les tests créent et suppriment des données (préfixées E2E-).
//
//   E2E_BASE_URL   : racine de l'application (défaut http://localhost:8080/ubisenderpro)
//   E2E_LOGIN      : compte de test (défaut admin)
//   E2E_MOT_DE_PASSE : son mot de passe (défaut Admin@2026, le mot de passe initial)
//
// Deux projets : « api » (aucun navigateur, contrats REST et non-régressions
// serveur) et « ui » (Chromium, parcours utilisateur dans l'interface ExtJS).
const { defineConfig } = require('@playwright/test');

// Le contexte d'application (/ubisenderpro) fait partie de l'URL : une barre
// oblique FINALE est indispensable pour que les chemins relatifs le conservent.
const BASE = (process.env.E2E_BASE_URL || 'http://localhost:8080/ubisenderpro').replace(/\/?$/, '/');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 45000,
  expect: { timeout: 10000 },
  // Les tests API partagent des données (créent puis relisent) : un seul worker
  // évite les surprises d'ordre ; la suite reste rapide (< 2 min).
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: BASE,
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'api', testDir: './tests/api' },
    { name: 'ui', testDir: './tests/ui', use: {
        baseURL: BASE,
        browserName: 'chromium',
        viewport: { width: 1440, height: 900 },
        // Un Chromium déjà présent sur la machine peut être désigné ici
        // (utile en environnement sans téléchargement de navigateurs) :
        //   E2E_CHROMIUM=/chemin/vers/chrome
        launchOptions: process.env.E2E_CHROMIUM
          ? { executablePath: process.env.E2E_CHROMIUM }
          : {},
      } },
  ],
});
