# UbiSenderPro

CRM, messagerie WhatsApp et campagnes marketing — **application 100 % autonome**
(aucune dépendance envers Prestige : base, code, utilisateurs et données séparés).

Ce dépôt couvre l'ensemble des lots des spécifications UbiSenderPro :
socle technique, référentiel clients, catalogue, WhatsApp Cloud API, conversations,
campagnes, CRM commercial et automatisations.

## Stack

| Couche | Technologie |
|---|---|
| Langage | Java 11 |
| Plateforme | Java EE 8 (JAX-RS, JPA, EJB, CDI) |
| Serveur | Payara Server 5 |
| JPA | EclipseLink (provider natif de Payara) |
| Base | MariaDB (`ubisenderpro_db`) |
| Migrations | Flyway |
| Import | Apache POI (Excel) + Commons CSV |
| Frontend | Ext JS 4.2 |

## Périmètre livré (Phase 1)

- Authentification par session + rôles (Administrateur, Marketing, Superviseur, Agent, Catalogue, Lecture seule)
- Comptes clients (`usp_client`) : recherche, pagination, CRUD
- Contacts (`usp_client_contact`) : plusieurs par compte, numéro WhatsApp, consentement, désabonnement
- Segmentations (`usp_segmentation_client`) avec normalisation des libellés
- **Assistant d'import Excel/CSV** des clients/contacts (sections 10 et 25 de la spec) :
  normalisation des numéros WhatsApp au format international, téléphones conservés en texte,
  gestion des doublons, rapport détaillé, journal d'import
- Journalisation des actions sensibles (`usp_journal_action`)
- Tableau de bord (indicateurs clients/contacts)

### Phase 2 (incluse)

- **Catalogue** : articles, catégories, marques, médias, stock indicatif avec mouvements,
  import Excel/CSV des articles
- **WhatsApp Cloud API** : comptes WhatsApp Business, envoi texte/média/modèle via la
  Graph API de Meta, webhook de réception (messages entrants + statuts envoyé/distribué/lu/échoué)
- **Conversations** : boîte de réception, affectation d'agent, notes internes, clôture/réouverture
- **Modèles de messages** + variables `{{...}}`
- **Listes de diffusion** statiques et **segments dynamiques** (évaluation par critères)
- **Campagnes** : ciblage liste/segment, suppression des doublons, exclusion des désabonnés,
  lancement réel via modèle, suivi des statuts par destinataire
- **CRM commercial** : opportunités (pipeline), commandes + lignes (création depuis conversation),
  numérotation automatique
- **Automatisations** : mots-clés (ex. STOP → désabonnement), message d'accueil

Schéma complet : **41 tables** (migrations Flyway V1 à V7).

## Mise en route

### 1. Base de données

```sql
CREATE DATABASE ubisenderpro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ubisenderpro'@'localhost' IDENTIFIED BY 'ubisenderpro';
GRANT ALL PRIVILEGES ON ubisenderpro_db.* TO 'ubisenderpro'@'localhost';
FLUSH PRIVILEGES;
```

> Le schéma est créé automatiquement par **Flyway** au premier démarrage
> (fichiers `src/main/resources/db/migration/V1..V3`). Inutile de lancer le SQL à la main.

### 2. Datasource Payara

Le fichier `src/main/webapp/WEB-INF/glassfish-resources.xml` déclare la datasource
`UbiSenderProDS`. Déposez le pilote MariaDB (`mariadb-java-client`) dans
`PAYARA_HOME/glassfish/domains/domain1/lib/` avant le déploiement.

Adaptez l'URL / utilisateur / mot de passe dans `glassfish-resources.xml` si besoin.

### 3. Frontend Ext JS

UbiSenderPro réutilise le SDK **Ext JS 4.2** déjà fourni avec Prestige. Copiez le dossier :

```
prestige/src/main/webapp/general/ext   ->   ubisenderpro/src/main/webapp/ext
```

`index.html` charge alors `ext/ext-all.js` et le thème Neptune compilé
(`ext/packages/ext-theme-neptune/build/resources/ext-theme-neptune-all.css`).
Le dossier `ext/` est volontairement ignoré par Git ; sans lui, l'interface affiche un
message d'erreur explicite au lieu d'une page blanche.

### 4. Build et déploiement

```bash
mvn clean package
# produit target/ubisenderpro.war -> à déployer sur Payara
```

URL : `http://localhost:8080/ubisenderpro`

> Guide de déploiement complet pas à pas (Payara 5, MariaDB, datasource, driver JDBC,
> webhook HTTPS, systemd, dépannage) : voir **`DEPLOIEMENT.md`**.

### 5. Première connexion

- Identifiant : `admin`
- Mot de passe : `Admin@2026` (paramètre `admin.mot_de_passe_initial`)

Le hash BCrypt est généré au premier démarrage. **Changez ce mot de passe** ensuite.

## Format du fichier client

Voir `exemples/clients_exemple.csv`. Colonnes minimales : `numero_client`, `nom_compte`.
Le mapping colonne → champ est configurable ; par défaut les noms logiques suivants sont
reconnus directement comme noms de colonnes :

```
numero_client, nom_compte, contact_principal, telephone_principal, telephone_2,
numero_whatsapp, fonction, agence, region, email_principal, segmentation,
adresse, ville, commune, pays, statut, notes, consentement_whatsapp
```

## API REST (Phase 1)

```
POST /api/v1/auth/login           Connexion (renvoie un jeton)
POST /api/v1/auth/logout          Déconnexion
GET  /api/v1/auth/me              Profil courant
GET  /api/v1/dashboard            Indicateurs
GET  /api/v1/clients             Liste paginée + filtres (q, agence, region, segmentationId)
GET  /api/v1/clients/{id}        Détail
GET  /api/v1/clients/{id}/contacts
POST /api/v1/clients             Création
PUT  /api/v1/clients/{id}        Modification
DELETE /api/v1/clients/{id}
GET  /api/v1/contacts/{id}
POST /api/v1/contacts
PUT  /api/v1/contacts/{id}
POST /api/v1/contacts/{id}/unsubscribe
POST /api/v1/contacts/{id}/subscribe
GET  /api/v1/segmentations
POST /api/v1/imports/clients      Import Excel/CSV (fichier en base64)
```

Toutes les routes (sauf `/auth/login`) exigent l'en-tête `Authorization: Bearer <jeton>`.

## Configuration WhatsApp Cloud API (Meta)

Prérequis côté Meta (à réaliser une fois) :

1. Créer un **compte Meta Business** et le faire **vérifier**.
2. Dans **Meta for Developers**, créer une application de type *Business* et ajouter le
   produit **WhatsApp**.
3. Récupérer le **Phone Number ID**, le **WhatsApp Business Account ID** et un
   **access token** (de préférence permanent via un utilisateur système).
4. Faire **approuver vos modèles de messages** (templates) par Meta — obligatoire pour
   tout envoi hors fenêtre de service client de 24h (campagnes notamment).

Enregistrement dans UbiSenderPro :

```
POST /api/v1/whatsapp/accounts
{
  "libelle": "Compte principal",
  "phoneNumberId": "123456789012345",
  "businessAccountId": "987654321098765",
  "numeroAffiche": "+225 07 00 00 00 00",
  "accessToken": "EAAG...",
  "verifyToken": "un-secret-de-votre-choix",
  "apiVersion": "v19.0"
}
```

Configuration du **webhook** dans la console Meta :

- URL de rappel : `https://VOTRE_DOMAINE/ubisenderpro/api/v1/webhooks/whatsapp`
- Jeton de vérification : la valeur `verifyToken` enregistrée ci-dessus
- Champs abonnés : `messages`

> Le endpoint `GET /webhooks/whatsapp` répond au défi de vérification Meta ;
> `POST /webhooks/whatsapp` reçoit les messages entrants et les mises à jour de statut.
> Le webhook doit être accessible en HTTPS depuis Internet (reverse proxy / tunnel).

## API REST (Phase 2)

```
# Catalogue
GET/POST/PUT/DELETE  /api/v1/articles            (+ POST /articles/{id}/stock)
GET/POST/PUT         /api/v1/catalogue/categories
GET/POST/PUT         /api/v1/catalogue/marques
POST                 /api/v1/imports/articles

# WhatsApp
GET/POST/PUT         /api/v1/whatsapp/accounts
POST                 /api/v1/whatsapp/messages/text
POST                 /api/v1/whatsapp/messages/media
GET/POST             /api/v1/webhooks/whatsapp     (appelé par Meta, non sécurisé)

# Conversations
GET                  /api/v1/conversations         (+ /{id}, /{id}/messages)
POST                 /api/v1/conversations/{id}/assign|close|reopen|read|notes

# Modèles, listes, segments
GET/POST/PUT/DELETE  /api/v1/templates
GET/POST/PUT         /api/v1/lists                 (+ /{id}/contacts)
GET/POST             /api/v1/segments              (+ /{id}/filtres, /{id}/preview)

# Campagnes
GET/POST/PUT         /api/v1/campaigns
POST                 /api/v1/campaigns/{id}/recipients|launch|pause|cancel

# CRM
GET/POST/PUT         /api/v1/opportunities         (+ /{id}/status)
GET/POST             /api/v1/orders                (+ confirm/prepare/ready/deliver/cancel)
GET/POST/PUT/DELETE  /api/v1/automations
```

## Interface ExtJS

Écrans livrés (`src/main/webapp/app/`) :

- `app.js` : login, menu, tableau de bord, grille des comptes clients, import
- `inbox.js` : **boîte de réception façon WhatsApp Web** en trois colonnes
  (conversations | discussion | fiche contact), envoi de texte, note interne,
  affectation, clôture/réouverture
- `campaign.js` : **assistant de campagne** en 5 étapes (informations → destinataires →
  contenu → programmation → validation) + grille de suivi des statuts
- `importer.js` : **assistant d'import générique** (détection de colonnes, mapping,
  modèles de mapping sauvegardés, modes, simulation, téléchargement des rejets)
- `catalogue.js` : articles (CRUD + stock + import), catégories, marques
- `crm.js` : **pipeline d'opportunités en Kanban avec glisser-déposer** + commandes
- `settings.js` : **comptes WhatsApp Business** et **modèles de messages** (config Meta depuis l'UI)

## Provisionnement manuel de la base (hors Flyway)

Un script SQL autonome est fourni : `src/main/resources/db/install/ubisenderpro_install.sql`
(toutes les tables + données de référence + compte admin avec hash BCrypt pré-calculé).

```bash
mysql -u root -e "CREATE DATABASE ubisenderpro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root ubisenderpro_db < src/main/resources/db/install/ubisenderpro_install.sql
```

Puis démarrer l'application **avec Flyway désactivé** pour ne pas rejouer les migrations :

```bash
export UBISENDERPRO_SKIP_FLYWAY=true      # ou -Dubisenderpro.skipFlyway=true
```

Connexion : `admin` / `Admin@2026` (hash déjà inséré ; à changer ensuite).

## Lancement asynchrone des campagnes

Le lancement de campagne est **asynchrone** :

- `POST /api/v1/campaigns/{id}/launch` répond **202 Accepted** immédiatement ;
- l'envoi s'exécute en arrière-plan via un EJB `@Asynchronous` (`CampagneSenderAsync`),
  **un envoi par transaction** (`CampagneSenderTx`, `REQUIRES_NEW`) pour éviter une
  transaction longue ;
- une **pause de débit** sépare deux envois (constante `PAUSE_MS`) ;
- la campagne peut être **suspendue/annulée** en cours : le worker s'arrête à la
  prochaine itération ;
- les statuts par destinataire (ENVOYE → DISTRIBUE → LU → ECHOUE) sont mis à jour par les
  webhooks Meta.

> Pour de très gros volumes, brancher en plus le respect des plages horaires autorisées
> et un découpage en lots planifiés (EJB Timer).

## Limites connues / pistes d'évolution

- L'assistant d'import générique complet (modèles de mapping persistés, modes
  remplacement/désactivation, téléchargement des lignes rejetées en fichier) reste à finaliser.
- Validation runtime non testée hors environnement Payara/MariaDB : seule la compilation
  est garantie.

## Extraction en dépôt dédié

Ce code n'a aucune dépendance vers Prestige. S'il a été initialement développé dans un
dossier d'un autre dépôt, il peut être extrait sans perte d'historique :

```bash
git subtree split --prefix=ubisenderpro -b ubisenderpro-export
```
