# Guide des tests — UbiSmartCRM Pro

Trois niveaux de tests protègent l'application contre les régressions. Chaque
niveau a son rôle, son coût et son moment d'exécution.

| Niveau | Où | Combien | Quand l'exécuter | Durée |
|---|---|---|---|---|
| **Unitaires & non-régression** | `src/test/java` (JUnit 5 + Mockito) | 130 tests | À **chaque build** (`mvn test`, automatique avant `mvn package`) | ~15 s |
| **Bout en bout API** | `e2e/tests/api` (Playwright) | 28 tests | Après chaque **déploiement** sur un environnement de test | ~6 s |
| **Bout en bout interface** | `e2e/tests/ui` (Playwright + Chromium) | 5 tests | Après chaque déploiement, avant une recette | ~6 s |

La recette manuelle (`docs/CAHIER_RECETTE_UAT.md`) reste la référence pour les
parcours complets impliquant WhatsApp, les imports réels et l'œil humain.

---

## 1. Tests unitaires et de non-régression (`mvn test`)

```bat
mvn test
```

Aucune base de données ni serveur requis : tout est simulé (Mockito) ou réel
mais autonome (POI, JasperReports). Ils s'exécutent d'office lors de
`mvn package` — **un livrable ne peut pas être construit avec un test rouge**.

Ce qu'ils verrouillent, entre autres :

- **Validation métier** : fiches client, promotions, licences, tickets… avec
  le message et le champ fautif attendus.
- **Non-régressions nommées** : dates vides tolérées (`JsonbProviderTest`),
  encodage ANSI/UTF-8 des imports (`FileParserApercuTest`), mise à jour
  sélective qui ne touche que les champs demandés (`MajSelectiveTest`),
  correspondance explicite des colonnes d'import (`ImportMappingProduitsTest`),
  tickets de consultation à usage unique (`TicketConsultationTest`).
- **Éditions** : les **22 modèles `.jrxml` embarqués compilent et produisent un
  vrai PDF** à chaque build (`RapportModelesTest`, `RapportClientsTest`) — une
  coquille dans un modèle ne peut plus atteindre la production.

Un seul test :

```bat
mvn test -Dtest=RapportModelesTest
```

---

## 2. Tests de bout en bout (`e2e/`, Playwright)

Ils s'exécutent contre une **instance déployée** (Payara + MySQL/MariaDB) et
valident l'application entière : REST, sécurité, base de données, génération
PDF/Excel, et l'interface dans un vrai Chromium.

> ⚠️ **Jamais contre la production.** Les tests créent puis suppriment des
> données (préfixées `E2E-`) et impriment des documents (archivés). Utilisez
> une base de test ou une copie.

### Installation (une fois)

```bat
cd e2e
npm install
npx playwright install chromium
```

### Exécution

```bat
cd e2e
npx playwright test              &REM tout (API + interface)
npx playwright test --project=api
npx playwright test --project=ui
```

### Configuration (variables d'environnement)

| Variable | Défaut | Rôle |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8080/ubisenderpro` | Racine de l'application testée |
| `E2E_LOGIN` | `admin` | Compte utilisé par les tests |
| `E2E_MOT_DE_PASSE` | `Admin@2026` | Son mot de passe |
| `E2E_CHROMIUM` | *(vide)* | Chemin d'un Chromium déjà installé (sinon celui de Playwright) |

Exemple contre un serveur de test :

```bat
set E2E_BASE_URL=http://192.168.1.50:8080/ubisenderpro
set E2E_MOT_DE_PASSE=MonMotDePasse
cd e2e && npx playwright test
```

### Ce que couvre la suite API (28 tests)

- **Authentification** : jeton délivré, mauvais mot de passe rejeté, endpoints
  protégés sans/avec faux jeton refusés.
- **Comptes clients** : création, champs obligatoires et e-mail contrôlés
  (400 + champ fautif), unicité du code, recherche, filtre tournée,
  **mise à jour sélective** (seuls les champs cochés changent),
  désactivation/réactivation, suppression.
- **Listes de diffusion** : import de codes en **simulation** (rien n'est
  écrit) puis réel, doublons comptés « déjà membres », **vidage**.
- **Assistant d'import** : détection des colonnes CSV UTF-8 **et ANSI
  (windows-1252)** — les accents survivent ; fichier illisible → 400 clair.
- **Éditions** : PDF `.jrxml` avec **lien de consultation nommé à usage
  unique** (2ᵉ lecture → 404), contenu réellement `%PDF` ; Excel réellement
  `.xlsx` ; relevé de compte ; modèle inconnu → 400 explicite.
- **Informations clients** : dates vides acceptées (non-régression du 500),
  date invalide → **400 métier** citant la valeur, titre manquant → champ.

### Ce que couvre la suite interface (5 tests)

Écran de connexion (affichage, refus, accès), navigation vers Comptes clients,
onglets (Listes de diffusion, **Mise à jour sélective** avec son volet
« Nouvelles valeurs »), menu **Exporter** (Excel + PDF).

---

## 3. Quand exécuter quoi

| Moment | Commande |
|---|---|
| Avant chaque commit / build | `mvn test` (automatique via `mvn package`) |
| Après déploiement sur l'environnement de test | `cd e2e && npx playwright test` |
| Avant une recette utilisateur | e2e complet **puis** `docs/CAHIER_RECETTE_UAT.md` |

## 4. Ajouter des tests

- **Unitaire** : une classe dans `src/test/java/...` (JUnit 5 ; fichiers 100 %
  ASCII — contrainte NetBeans — accents en `\uXXXX` dans les chaînes).
- **e2e API** : un fichier `e2e/tests/api/xxx.spec.js` ; utilisez
  `connexion()`/`entetes()` de `tests/aide.js`, préfixez les données `E2E-`
  et nettoyez-les dans `afterAll`.
- **e2e interface** : `e2e/tests/ui/xxx.spec.js` ; privilégiez les repères
  stables (`#str_login`, libellés) et des attentes explicites.
