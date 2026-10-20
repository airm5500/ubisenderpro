# Tests de bout en bout (niveaux 2, 3 et 5)

Suite Playwright lancee contre une instance demarree du CRM, sur une base de test
(jamais la production : elle cree des donnees prefixees `E2E-`).

| Projet | Niveau | Contenu |
| --- | --- | --- |
| `api` | 2. E2E API | routes REST, cloisonnement des modules, codes d'erreur |
| `ui` | 3. E2E interface | parcours dans les ecrans |
| `saisies` | 5. UI/UX et controles de saisie | champs obligatoires, messages d'erreur |

```
cd crm/e2e
npm ci
npx playwright install chromium     (une fois, ou E2E_CHROMIUM=/chemin/vers/chrome)
npx playwright test                 (les trois niveaux)
npx playwright test --project=api   (un seul niveau)
```

Variables : `E2E_BASE_URL` (defaut `http://localhost:8080/`), `E2E_LOGIN` (defaut `admin`),
`E2E_MOT_DE_PASSE` (defaut `E2E-Test@2026`). Sur une base neuve, le mot de passe initial
`Admin@2026` du compte est remplace par `E2E_MOT_DE_PASSE` avant la suite.

Avec les niveaux 1 et 4 (`./mvnw verify -Pit`, qui lance aussi les tests unitaires) et
`npx ng test --watch=false` dans `crm/front`, c'est la suite complete a lancer a chaque livraison.
