# Revue d'autorisation endpoint par endpoint — UbiSmartCRM Pro v2.0

> Audit systématique des 46 ressources REST (`/api/v1/**`) : qui peut appeler
> quoi, avec quel rôle ou quelle permission. Réalisé sur la branche
> `claude/tender-wozniak-nnzz3j`, après les modules Support et Licence (V56).

---

## 1. Comment lire cette revue (mécanique de sécurité)

- L'annotation **`@Secured`** est un *NameBinding* JAX-RS : le filtre
  d'authentification **ne s'exécute que sur les classes/méthodes annotées**.
  Conséquence directe : **un endpoint sans `@Secured` est public** (aucune
  session exigée). C'est le premier axe de l'audit.
- `@Secured` seul = **session valide requise** (n'importe quel utilisateur
  connecté). `@Secured(roles = {...})` = session + un des rôles listés.
  `@Secured(menu = "...", action = "...")` = session + permission RBAC fine
  (l'action est déduite de la méthode HTTP si non précisée : GET→VOIR,
  POST→CREER, PUT→MODIFIER, DELETE→SUPPRIMER, activate/annul/archiv→DESACTIVER,
  dupliquer/import→CREER).
- L'annotation de **méthode prime** sur celle de la classe (pour les rôles
  comme pour le menu).
- S'ajoute le **verrou de licence** (AuthenticationFilter) : POST/PUT sur
  `campaigns/`, `wa-bulk`, `recouvrement/envois`, `recouvrement/campagnes/envoyer`,
  `imports`, `propositions/`, `recouvrement/propositions`, `infos/`,
  `dispo-evenements/` → 403 si `licence.obligatoire=true` et licence absente /
  invalide / expirée au-delà de la grâce.
- Convention générale du produit : **lectures ouvertes à toute session**
  (l'interface masque les menus non autorisés), **écritures contrôlées par
  rôle ou par permission**. La revue vérifie que cette convention est
  réellement tenue côté serveur.

## 2. Corrections apportées par cette revue

| # | Constat | Gravité | Correction |
|---|---|---|---|
| C1 | `GET /parametres/{cle}` permettait à **tout utilisateur connecté** de lire n'importe quelle clé, y compris `mail.smtp.password` ou `support.mot_de_passe_initial` | 🔴 | Les clés sensibles (motifs `password`, `mot_de_passe`, `secret`, `token`, `api_key` et tout `mail.smtp.*`) renvoient **403 sauf ADMIN**. Les clés lues par l'interface (`app.*`, `delai_deconnexion`, `whatsapp.mode_envoi`, `whatsapp.prefixe_pays`, `dashboard.prefs_defaut`) restent ouvertes |
| C2 | `GET /whatsapp/accounts` (toute session) renvoyait **l'access token Meta** de chaque compte — le jeton donne le contrôle total du numéro WhatsApp Business, et les écrans Campagnes/Inbox chargent cette liste pour tous les profils | 🔴 | `accessToken` passe en **WRITE_ONLY** (accepté en entrée, jamais sérialisé). À la modification d'un compte, un jeton laissé vide **conserve** le jeton actuel (service) ; le formulaire Paramètres l'indique |
| C3 | Transitions de commande (`/orders/{id}/confirm|prepare|ready|deliver|cancel`) accessibles à **toute session** alors que la création exige ADMIN/SUPERVISEUR/AGENT | 🟠 | Les 5 transitions exigent désormais les **mêmes rôles que la création** |
| C4 | Écritures CRM (`POST/PUT /opportunities`, `POST /{id}/status`) accessibles à toute session (un profil LECTURE pouvait modifier des opportunités par appel direct) | 🟠 | Rôles alignés sur le menu `crm` : **ADMIN, SUPERVISEUR, AGENT, MARKETING** |
| C5 | Actions de traitement des discussions (`assign`, `close`, `reopen`, `read`, `bot-on/off`, `notes`, `premier-contact`) accessibles à toute session | 🟠 | Rôles alignés sur le menu `inbox` : **ADMIN, MARKETING, SUPERVISEUR, AGENT** |

> Note « zéro régression » : les rôles retenus reproduisent exactement les
> rôles qui voient déjà ces écrans (`rolesDuMenu`) — aucun utilisateur légitime
> ne perd de fonction ; seuls les appels directs hors interface sont fermés.
> Le menu `crm` n'ayant pas d'actions CREER/MODIFIER dans le RBAC, une
> protection par `menu="crm"` aurait bloqué tout le monde (ADMIN compris) :
> c'est pourquoi C4/C5 passent par les rôles.

## 3. Endpoints publics **par conception** (assumés)

| Endpoint | Pourquoi public | Garde-fou |
|---|---|---|
| `GET /about` | La page de connexion affiche nom/version | Ne divulgue que nom, version, développeur |
| `POST /auth/login` | Point d'entrée de l'authentification | Mots de passe hachés (BCrypt), journal des connexions |
| `GET /media/{id}` | **WhatsApp (Meta) récupère les médias par lien** pour les envois « média par URL » ; le navigateur les affiche sans en-tête Authorization | Ids numériques opaques ; contenu uploadé par des utilisateurs authentifiés uniquement |
| `GET/POST /webhooks/whatsapp` | Rappels entrants de Meta | GET validé par `hub.verify_token` ; POST : traitement best-effort qui ne fait que persister des événements |
| `POST /webhooks/wa-web/*` | Rappels du serveur WhatsApp Web local | En-tête **X-Api-Token** exigé et vérifié |

**Recommandations (non bloquantes)** : ① servir les médias sous un identifiant
aléatoire (UUID) plutôt que séquentiel pour empêcher l'énumération ; ② valider
la signature `X-Hub-Signature-256` des webhooks Meta.

## 4. Table de référence endpoint par endpoint

Légende : *Session* = tout utilisateur connecté · *menu:action* = permission
RBAC · 🔓 = public assumé (§3) · 🔒L = soumis en plus au verrou de licence.

### Authentification & socle

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/about` | GET | 🔓 |
| `/auth/login` | POST | 🔓 |
| `/auth/logout`, `/auth/navigation`, `/auth/me` | POST/GET | Session |
| `/dashboard`, `/dashboard/series` | GET | Session |
| `/notifications` | GET | Session |
| `/parametres` | GET | ADMIN |
| `/parametres/{cle}` | GET | Session — **403 sur clés sensibles sauf ADMIN** (C1) |
| `/parametres/{cle}` | PUT | ADMIN |
| `/permissions/me` | GET | Session |
| `/permissions/menus`, `/permissions/roles/{code}` | GET/PUT | ADMIN |

### Utilisateurs & rôles

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/users` (classe) | GET | menu `users:VOIR` (liste, connexions, journal, activité, photo) |
| `/users` | POST | menu `users:CREER` |
| `/users/{id}` | PUT | menu `users:MODIFIER` |
| `/users/{id}/activate`, `/deactivate` | POST | menu `users:DESACTIVER` |
| `/users/{id}/reset-password` | POST | menu `users:MODIFIER` |
| `/users/affectables` | GET | menu `inbox:VOIR` (sélecteur d'agent de l'inbox) |
| `/users/roles` | GET | menu `users:VOIR` — POST/PUT rôles : ADMIN |

### Clients, contacts, segmentations

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/clients` + `/facettes`, `/selection`, `/{id}`, `/{id}/contacts` | GET | Session |
| `/clients` | POST/PUT | menu `clients` |
| `/clients/{id}` | DELETE | ADMIN |
| `/clients/{id}/numeros`, `/activate`, `/deactivate` | POST | menu `clients` |
| `/contacts/selection`, `/contacts/{id}` | GET | Session |
| `/contacts` écritures (`POST`, `PUT`, `subscribe`, `unsubscribe`) | POST/PUT | menu `clients` |
| `/contacts/{id}` | DELETE | ADMIN |
| `/segmentations` | GET | Session — écritures : menu `clients` |
| `/segments` | GET/préview | Session — écritures : ADMIN, MARKETING |
| `/lists` | GET | Session — écritures : ADMIN, MARKETING |

### Catalogue & articles

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/articles`, `/articles/{id}`, `/cip7-libre`, `/promo` | GET | Session |
| `/articles` | POST/PUT | menu `catalogue` |
| `/articles/{id}/stock` | POST | menu `catalogue:AJUSTER_STOCK` |
| `/articles/promo` | POST | menu `catalogue:MAJ_PROMO` |
| `/articles/{id}` | DELETE | ADMIN |
| `/catalogue/categories`, `/marques` | GET | Session — écritures : menu `catalogue` |

### Marketing, campagnes, promotions

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/campaigns` lectures (liste, détail, stats, destinataires, performance) | GET | Session |
| `/campaigns` écritures (créer, modifier, recipients, launch, resume, pause, cancel, relancer, supprimer) | POST/PUT/DELETE | menu `campaigns` (+ `RENVOI_ECHECS` pour relancer) 🔒L |
| `/templates` lectures (+ export docx) | GET | Session |
| `/templates` écritures (+ import-docx) | POST/PUT/DELETE | menu `marketing` |
| `/promotions` lectures (liste, détail, produits) | GET | Session |
| `/promotions` écritures (CRUD, dupliquer, annuler, archiver, produits, import) | POST/PUT/DELETE | menu `promotions` |
| `/propositions` | GET | Session 🔒L |
| `/propositions/generer`, `valider`, `rejeter`, `rejeter-lot` | POST | menu `marketing` 🔒L |
| `/propositions/{id}` | DELETE | menu `marketing:SUPPRIMER` |
| `/automations` | tous | ADMIN, MARKETING |
| `/dispo-evenements`, `/dispo-regles`, `/infos` | GET | Session — écritures : menus `dispo` / `infos` 🔒L |

### WhatsApp (API Cloud, Web, envois de masse)

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/whatsapp/accounts`, `/{id}/templates` | GET | Session — **accessToken jamais sérialisé** (C2) |
| `/whatsapp/accounts` | POST | ADMIN — PUT/DELETE : menu `settings` |
| `/whatsapp/messages/text`, `/media`, `/whatsapp/media` | POST | ADMIN, MARKETING, SUPERVISEUR, AGENT |
| `/wa-web/**` (sessions, envoi, contacts, groupes, warmup) | tous | ADMIN, MARKETING (classe) |
| `/wa-bulk` lectures | GET | menu `waweb:VOIR` |
| `/wa-bulk` créer/lancer/préparer | POST | menu `waweb:ENVOI_MASSE` 🔒L — relancer : `RENVOI_ECHECS` |
| `/webhooks/whatsapp`, `/webhooks/wa-web/*` | GET/POST | 🔓 (§3) |

### Inbox, CRM, commandes

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/conversations`, `/{id}`, `/{id}/messages` | GET | Session |
| `/conversations/{id}/assign`, `close`, `reopen`, `read`, `bot-on`, `bot-off`, `notes`, `/premier-contact` | POST | **ADMIN, MARKETING, SUPERVISEUR, AGENT** (C5) |
| `/conversations/{id}` | DELETE | ADMIN, SUPERVISEUR, MARKETING |
| `/opportunities`, `/{id}` | GET | Session |
| `/opportunities` écritures (créer, modifier, statut) | POST/PUT | **ADMIN, SUPERVISEUR, AGENT, MARKETING** (C4) |
| `/orders`, `/orders/{id}` | GET | Session |
| `/orders` + 5 transitions de statut | POST | **ADMIN, SUPERVISEUR, AGENT** (C3) |
| `/bot/faq` (administration du bot) | tous | ADMIN |

### Historique, imports, référentiels, médias

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/historique` | GET | menu `historique:VOIR` |
| `/imports/**` (clients, articles, mappings, journaux, rejets) | tous | ADMIN, MARKETING, CATALOGUE (classe) 🔒L |
| `/referentiels/{type}` | GET | Session — écritures/import : ADMIN |
| `/media/upload` | POST | ADMIN, MARKETING, SUPERVISEUR, AGENT |
| `/media/{id}` | GET | 🔓 (§3 — récupération par WhatsApp) |

### Recouvrement

| Endpoint | Méthodes | Accès |
|---|---|---|
| Toutes les lectures (`fiches`, `dashboard`, `modeles`, `envois`, `propositions`, `referentiels`, `clients/{id}/…`) | GET | menu `recouvrement:VOIR` (classes) |
| Fiches / créances / paiements / promesses / modèles | POST/PUT/DELETE | `recouvrement:CREER / MODIFIER / SUPPRIMER` |
| `/recouvrement/envois`, `/campagnes/envoyer`, `/propositions/{id}/valider` | POST | `recouvrement:ENVOYER` 🔒L |
| `/recouvrement/import/**` | POST | `recouvrement:IMPORTER` (classe) |
| `/recouvrement/referentiels` écritures | POST/PUT | `recouvrement:GERER_REFERENTIELS` — imports : `IMPORTER` |
| `/recouvrement/campagnes/preview`, `/propositions/generer|rejeter` | POST | `VOIR` / `CREER` / `MODIFIER` |

### Centre de support & Licence

| Endpoint | Méthodes | Accès |
|---|---|---|
| `/support/demandes` | POST | Session (tout utilisateur peut contacter l'éditeur) |
| `/support/demandes` | GET | ADMIN, SUPPORT |
| `/support/tickets` | POST/GET | Session — la liste complète (`mine=false`) et le détail sont filtrés **en code** : un utilisateur ne voit que SES tickets (`peutVoir`), l'équipe voit tout |
| `/support/tickets/{id}/statut`, `/affecter` | PUT | ADMIN, SUPPORT |
| `/support/events` | POST | Session (collecte JS) — GET/ticket/purge : ADMIN, SUPPORT |
| `/support/sante` | GET | ADMIN, SUPPORT |
| `/support/faq` | GET | Session |
| `/licence/etat` | GET | Session (bandeau d'alerte pour tous) |
| `/licence/importer`, `/demande`, `/evenements` | POST/GET | ADMIN, SUPPORT |

## 5. Verdict

- **Aucun endpoint involontairement public** : les 5 chemins sans `@Secured`
  sont tous justifiés et gardés autrement (§3).
- **2 fuites de secrets corrigées** (paramètres sensibles, jeton Meta) et
  **3 écritures resserrées** au niveau des rôles qui voient déjà les écrans.
- Les contrôles sensibles sont bien **côté serveur** (rôles, permissions,
  propriété des tickets, verrou de licence), pas seulement dans l'interface.
