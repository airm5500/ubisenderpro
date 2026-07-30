# Guide complet — Gestion des Licences

> **UbiSmartCRM Pro · v2.0** — menu **🔑 Licence** + outil éditeur **UbiLicense Manager**
>
> Ce guide couvre **tout** : le principe, la configuration, la mise en place
> côté éditeur (génération des clés et des licences), le manuel d'utilisation
> côté client, l'exploitation courante (renouvellement, transfert de serveur),
> le dépannage et les références rapides.

## Sommaire

1. [Comprendre le système en 2 minutes](#1-comprendre-le-système-en-2-minutes)
2. [Configuration de l'application](#2-configuration-de-lapplication)
3. [Mise en place côté éditeur (une seule fois)](#3-mise-en-place-côté-éditeur-une-seule-fois)
4. [UbiLicense Manager — référence des champs](#4-ubilicense-manager--référence-des-champs)
5. [Première activation d'un client (pas à pas)](#5-première-activation-dun-client-pas-à-pas)
6. [Manuel d'utilisation côté client](#6-manuel-dutilisation-côté-client)
7. [Ce qui se passe à l'expiration](#7-ce-qui-se-passe-à-lexpiration)
8. [Renouvellement & transfert de serveur](#8-renouvellement--transfert-de-serveur)
9. [Sécurité & bonnes pratiques](#9-sécurité--bonnes-pratiques)
10. [Dépannage (messages d'erreur & solutions)](#10-dépannage-messages-derreur--solutions)
11. [Références rapides](#11-références-rapides)

---

## 1. Comprendre le système en 2 minutes

- Une **licence** est une **charge utile JSON signée** cryptographiquement
  (RSA 2048 / SHA-256) par l'éditeur.
- L'application **ne génère jamais** de licence : elle se contente de
  **vérifier** la signature avec une **clé publique embarquée** dans le WAR
  (`src/main/resources/licence/public.pem`).
- La **clé privée** — la seule capable de signer — reste chez l'éditeur, dans
  l'outil séparé **UbiLicense Manager**, **jamais livré au client**.

**Conséquence :** une licence est **infalsifiable**. Modifier un seul caractère
(par exemple avancer la date d'expiration) casse la signature → la licence est
refusée. C'est vérifié par un test automatique.

```
        ÉDITEUR (clé privée)                     CLIENT (clé publique embarquée)
   ┌─────────────────────────┐              ┌──────────────────────────────┐
   │  UbiLicense Manager      │   .lic /     │  UbiSmartCRM Pro             │
   │  - saisit société,       │   clé        │  menu 🔑 Licence             │
   │    dates, modules        │  ─────────►  │  - vérifie la signature      │
   │  - SIGNE avec private.pem │              │  - applique les restrictions │
   └─────────────────────────┘              └──────────────────────────────┘
             ▲                                          │
             │            REQUEST.licreq                │
             └──────────  (empreinte serveur)  ◄────────┘
```

Le format d'une licence : `base64url(payloadJson) + "." + base64url(signature)`.
Deux présentations **équivalentes** : une **clé d'activation** (longue chaîne à
copier-coller) ou un fichier **`.lic`**.

---

## 2. Configuration de l'application

Tout se règle dans **Paramètres** (menu Paramètres, réservé ADMIN). Deux clés
seulement pilotent le régime de licence :

| Clé | Défaut | Rôle |
|---|---|---|
| `licence.obligatoire` | `false` | Interrupteur général du régime de licence |
| `licence.grace_jours` | `7` | Nombre de jours de tolérance après l'expiration |

### 2.1 L'interrupteur clé : `licence.obligatoire`

| Valeur | Comportement |
|---|---|
| **`false`** *(défaut)* | **Aucune restriction.** L'application fonctionne exactement comme sans module de licence — avec ou sans licence installée. C'est le mode **« zéro régression »** : un déploiement existant n'est pas impacté tant que vous ne l'activez pas. Un bandeau **informatif** peut apparaître, sans jamais rien bloquer. |
| **`true`** | Le **régime commercial** s'applique : les menus sont filtrés selon les modules de la licence, et les envois/automatisations/imports sont bloqués après expiration (grâce comprise) ou en l'absence de licence valide. |

> **Recommandation :** déployez d'abord en `false`, importez la licence,
> vérifiez l'état **Active**, puis basculez `licence.obligatoire=true`. Vous
> évitez toute coupure pendant l'installation.

### 2.2 La période de grâce : `licence.grace_jours`

Après la date d'expiration, l'application laisse un **délai de tolérance**
(défaut **7 jours**) pendant lequel **tout continue de fonctionner**, avec un
bandeau d'alerte. C'est le temps de renouveler sans interruption. Passé ce
délai, les fonctions d'envoi se bloquent.

### 2.3 La base de données

La migration **V55** (appliquée automatiquement au démarrage) crée :

- `usp_licence` — l'état de la licence courante (payload, signature, dates,
  dernière date vue…) ;
- `usp_licence_evenement` — le **journal** (activation, renouvellement, refus,
  demandes générées).

Ces tables sont **isolées et additives** : aucun impact sur l'existant.

---

## 3. Mise en place côté éditeur (une seule fois)

### 3.1 Construire l'outil

```bash
mvn -f tools/ubilicense-manager/pom.xml package
java -jar tools/ubilicense-manager/target/ubilicense-manager.jar
```

C'est un projet Maven **autonome** : il n'affecte pas le build du WAR principal.

> ⚠️ **UbiLicense Manager détient la clé privée.** Ne le livrez **jamais** au
> client et ne l'incluez **jamais** dans le WAR.

### 3.2 Générer VOS clés de production

La paire `cles/private_DEV.pem` fournie dans le dépôt sert **uniquement aux
tests** (elle est appariée à la clé publique embarquée par défaut). **Pour la
production, générez votre propre paire :**

1. Onglet **🗝️ Clés** → **« Générer une nouvelle paire de clés… »** → choisir
   un dossier **hors dépôt Git**.
2. Deux fichiers sont créés :
   - **`private.pem`** → à conserver **en lieu sûr** (coffre, support chiffré,
     sauvegardé). Sa **fuite compromet tout le système** ; sa **perte** empêche
     d'émettre de nouvelles licences (celles déjà émises restent valides).
   - **`public.pem`** → à copier dans l'application :
     `src/main/resources/licence/public.pem`, puis **rebuilder le WAR**.

> ⚠️ Régénérer les clés **invalide** toutes les licences émises avec l'ancienne
> paire. On ne le fait qu'une fois, au tout début.

---

## 4. UbiLicense Manager — référence des champs

Onglet **🔑 Licence** : tous les champs de la licence à émettre.

| Champ | Exemple / défaut | Signification |
|---|---|---|
| **Clé privée** | `cles/private.pem` | Chemin de la clé qui **signe** (bouton pour parcourir) |
| **Identifiant client** | `CLI-0001` | Votre référence interne du client |
| **Société** | *(vide)* | Raison sociale (rempli auto depuis le `.licreq`) |
| **Pays** | `Côte d'Ivoire` | Pays du client |
| **E-mail** | *(vide)* | Contact client (rempli auto depuis le `.licreq`) |
| **Type** | `ESSAI` / `STANDARD` / `PRO` / `ENTREPRISE` | Libellé commercial (informatif) |
| **Activation** | *(aujourd'hui)* | Date de début de validité (AAAA-MM-JJ) |
| **Expiration** | *(aujourd'hui + 1 an)* | Date de fin de validité (AAAA-MM-JJ) |
| **Max utilisateurs** | `10` | Limite indicative d'utilisateurs |
| **Max agences** | `5` | Limite indicative d'agences |
| **Version min** | `2.0.0` | Version d'appli minimale couverte |
| **Version max** | *(vide)* | Version maximale (vide = pas de plafond) |
| **Empreinte serveur** | *(vide)* | `SRV-XXXXXXXX` du serveur cible (rempli auto depuis le `.licreq`). **Vide = licence utilisable sur n'importe quel serveur** |
| **Modules activés** | *(tous cochés)* | 12 cases à cocher (voir §11). **Aucune coche = tous les modules** |

**Boutons :**

- **« Charger .licreq… »** — importe la demande du client : remplit
  automatiquement l'**empreinte serveur**, la **société** et l'**e-mail**.
- **« 🔏 Générer la licence (.lic) »** — signe et propose d'enregistrer le
  fichier `LICENSE-XXX.lic`. La **clé d'activation** équivalente s'affiche aussi
  dans la zone de texte (copiable). Chaque génération est tracée dans
  `licences_generees.log` (horodatage + contenu).

> **Astuce empreinte :** laissez l'empreinte **vide** pour une licence
> « flottante » (n'importe quel serveur). Renseignez-la (via le `.licreq`) pour
> **verrouiller** la licence à une machine précise — recommandé en production.

---

## 5. Première activation d'un client (pas à pas)

Flux **hors ligne** (recommandé, fonctionne même pour un site sans accès direct
à l'éditeur).

### Étape A — Déploiement (éditeur ou intégrateur)

1. Déployer le WAR chez le client (la migration V55 s'applique seule).
2. Laisser `licence.obligatoire=false` pour l'instant.

### Étape B — Le client génère sa demande

| Qui | Action |
|---|---|
| **Client (ADMIN/SUPPORT)** | Menu **🔑 Licence → 📤 Générer une demande (.licreq)** → saisir le nom de la société → le fichier **`REQUEST.licreq`** se télécharge. Il contient l'identité + **l'empreinte du serveur**. Le transmettre à l'éditeur (e-mail, clé USB…). |

### Étape C — L'éditeur émet la licence

| Qui | Action |
|---|---|
| **Éditeur** | UbiLicense Manager → onglet **🔑 Licence** → **« Charger .licreq… »** (l'empreinte, la société et l'e-mail se remplissent) → compléter **type, dates, limites, modules cochés** → **« 🔏 Générer la licence »** → enregistrer `LICENSE-XXX.lic` (ou copier la clé d'activation affichée). Renvoyer au client. |

### Étape D — Le client active

| Qui | Action |
|---|---|
| **Client (ADMIN/SUPPORT)** | Menu **🔑 Licence → 📥 Importer une licence** → **coller la clé** *ou* **charger le `.lic`** → **Activer**. La signature est vérifiée **localement** ; l'état passe à **Active**. |

### Étape E — Activer le régime et vérifier

1. **Paramètres → `licence.obligatoire` = `true`**.
2. **Licence → 🔑 État & activation** : vérifier statut **Active**, échéance,
   modules, empreinte.
3. Se reconnecter avec un utilisateur : les **menus hors modules licenciés ont
   disparu**, et un appel direct à leurs API renvoie **403**.
4. **Licence → 🗂️ Journal** : l'activation est tracée.

---

## 6. Manuel d'utilisation côté client

Le menu **🔑 Licence** (réservé **ADMIN** et **SUPPORT**) comporte deux onglets.

### 6.1 Onglet « 🔑 État & activation »

Affiche l'état courant sous forme de tableau :

- **Statut** (coloré) et son message,
- Société, Identifiant client, Type,
- Dates d'activation / d'expiration + **jours restants**,
- Max utilisateurs / agences,
- **Modules** autorisés,
- **Empreinte du serveur** (`SRV-XXXXXXXX`).

Deux boutons :

- **📥 Importer une licence** — ouvre une fenêtre : collez la clé d'activation
  **ou** chargez le fichier `.lic`, puis **Activer**. En cas de succès, un
  message confirme et le bandeau d'alerte se recalcule.
- **📤 Générer une demande (.licreq)** — saisissez votre société, le fichier
  `REQUEST.licreq` se télécharge (à transmettre à l'éditeur).

### 6.2 Onglet « 🗂️ Journal »

Historique local des événements de licence : **activations**, **renouvellements**,
**refus** (avec le motif), **demandes** générées — avec date et utilisateur.
Utile pour l'audit et le support.

### 6.3 Le bandeau d'alerte (tous les utilisateurs)

Un bandeau global apparaît automatiquement en haut de l'application :

- **Orange** dès **J-30** puis pendant la **période de grâce** ;
- **Rouge** après **expiration**, licence **invalide** ou **horloge suspecte**.

Il inclut un lien direct vers l'écran Licence. L'état est revérifié
automatiquement **toutes les heures**.

---

## 7. Ce qui se passe à l'expiration

> Rappel : rien de tout cela ne s'applique tant que `licence.obligatoire=false`.

| Moment | Statut | Effet concret |
|---|---|---|
| Plus de 30 j avant l'échéance | **ACTIVE** | Fonctionnement normal |
| J-30 → échéance | **EXPIRE_BIENTOT** | Bandeau **orange** « expire bientôt » (le compteur de jours rend J-30/15/7/1 visibles) — **tout fonctionne** |
| Échéance → +`grace_jours` | **GRACE** | Bandeau **orange** « période de grâce » — **tout fonctionne encore** |
| Après la grâce | **EXPIREE** | **Blocage** : envois (campagnes, WhatsApp Web, relances de recouvrement), **automatisations** (ordonnanceurs suspendus), **imports de masse**. Message explicite : *« Licence expirée ou absente : les envois, automatisations et imports sont désactivés. Renouvelez la licence (menu Licence). »* |

**Ce qui reste TOUJOURS accessible**, même licence expirée :

- La **consultation** des données (lecture),
- L'**administration** (Paramètres, Utilisateurs),
- L'écran **🔑 Licence** (pour renouveler !),
- Le **Centre de support** et le rôle **SUPPORT**.

Les **menus « socle »** ne sont **jamais** filtrés par la licence :
`dashboard`, `settings` (Paramètres), `users` (Utilisateurs), `support`,
`licence`.

### 7.1 Mode maintenance officiel

Le rôle **SUPPORT** reste pleinement fonctionnel licence expirée (diagnostic,
tickets, **renouvellement de licence**). C'est le mode maintenance **traçable**,
**sans compte caché** ni porte dérobée.

---

## 8. Renouvellement & transfert de serveur

### 8.1 Renouveler

1. L'éditeur génère une **nouvelle licence** (mêmes étapes qu'en §5.C, avec de
   **nouvelles dates**).
2. Le client l'**importe** (📥 Importer une licence).
3. **Prise d'effet immédiate. Aucune réinstallation, aucun redémarrage.**

Les restrictions éventuelles sont levées instantanément dès l'import d'une
licence valide.

### 8.2 Changer de serveur (transfert)

L'empreinte serveur (`SRV-XXXXXXXX`) est dérivée du **nom d'hôte + carte
réseau**. Elle change donc si l'on change de machine.

1. Sur le **nouveau** serveur : **📤 Générer une demande (.licreq)** (nouvelle
   empreinte).
2. L'éditeur émet une **nouvelle licence** avec cette nouvelle empreinte.
3. **Importer** sur le nouveau serveur.

L'ancienne licence devient inutilisable ailleurs (l'empreinte ne correspondra
plus) — c'est la protection anti-copie.

> **Empreinte « tolérante » :** le contrôle n'a lieu que **si** l'empreinte est
> présente dans la licence. En cas de souci matériel, une réémission suffit —
> pas de blocage sournois. Pour une licence sans verrou machine, laissez
> simplement l'empreinte vide à la génération.

---

## 9. Sécurité & bonnes pratiques

- 🔒 **Clé privée = actif critique.** Stockez `private.pem` dans un coffre,
  sur support chiffré, avec sauvegarde. Ne la mettez **jamais** dans Git ni
  dans le WAR.
- 🚫 **Ne livrez jamais UbiLicense Manager** au client.
- 🧪 La paire `private_DEV.pem` / `public.pem` du dépôt est **de test
  uniquement**. En production, **générez votre propre paire** et remplacez la
  clé publique embarquée.
- 🕵️ **Anti-recul d'horloge :** l'application mémorise la plus grande date vue.
  Un recul d'horloge **> 24 h** bascule la licence en statut **HORLOGE**
  (restrictions actives) jusqu'à correction — protège contre la triche sur
  l'expiration.
- ✅ **Vérification 100 % locale :** aucune connexion à un serveur de licence
  n'est requise. Idéal pour les sites cloisonnés.
- 🔐 **Verrou machine en production :** renseignez l'empreinte (via le
  `.licreq`) pour lier chaque licence à son serveur.

---

## 10. Dépannage (messages d'erreur & solutions)

| Message affiché | Cause | Solution |
|---|---|---|
| **« Signature invalide : cette licence n'a pas été émise par l'éditeur. »** | Clé publique embarquée ≠ clé privée signataire, **ou** contenu altéré | Vérifier que `public.pem` du WAR correspond à la `private.pem` utilisée. Ne jamais éditer une licence à la main. |
| **« Format de licence invalide (payload.signature attendu). »** | La chaîne collée n'a pas la forme `payload.signature` | Recopier **l'intégralité** de la clé, sans espace ni coupure ; ou charger le `.lic`. |
| **« Encodage de la licence illisible. »** | Copier-coller corrompu (retour à la ligne, caractères parasites) | Re-télécharger le `.lic` et le charger directement plutôt que copier. |
| **« Cette licence est liée à un autre serveur (empreinte attendue : X, ce serveur : Y). Demandez un transfert à l'éditeur. »** | Licence verrouillée sur une autre machine | Générer un nouveau `.licreq` **sur ce serveur** et demander une réémission (§8.2). |
| **« Cette licence ne couvre pas la version X de l'application. »** | Version de l'appli hors de `versionMin`/`versionMax` | Émettre une licence couvrant la version, ou aligner la version d'appli. |
| **Statut HORLOGE** | Horloge système reculée de plus de 24 h | Corriger l'heure du serveur (NTP recommandé). |
| **Menus manquants pour un utilisateur** | `licence.obligatoire=true` + module non inclus dans la licence | Émettre une licence incluant le module, ou ajuster les cases « Modules activés ». |
| **Envois bloqués (403)** | Licence expirée (grâce dépassée) / absente / invalide | Importer une licence valide (§8.1). Vérifier l'état dans 🔑 Licence. |
| **Le bandeau ne disparaît pas après import** | Cache d'affichage | Il se recalcule après l'import ; sinon rafraîchir (F5) — la revérification est horaire. |

---

## 11. Références rapides

### Paramètres

| Clé | Défaut | Rôle |
|---|---|---|
| `licence.obligatoire` | `false` | `true` = régime de licence actif (modules + blocages) |
| `licence.grace_jours` | `7` | Période de grâce après expiration |

### Statuts possibles

`AUCUNE` · `ACTIVE` · `EXPIRE_BIENTOT` (≤ 30 j) · `GRACE` · `EXPIREE` ·
`INVALIDE` (signature / empreinte / version) · `HORLOGE` (recul d'horloge).

### Modules licenciables (codes)

`inbox`, `clients`, `catalogue`, `promotions`, `marketing`, `dispo`, `infos`,
`campaigns`, `waweb`, `historique`, `crm`, `recouvrement`
→ **aucune coche / liste vide = tous les modules**.

**Menus socle jamais filtrés :** `dashboard`, `settings`, `users`, `support`,
`licence`.

### Types (libellés commerciaux)

`ESSAI` · `STANDARD` · `PRO` · `ENTREPRISE` (informatifs — n'influencent pas les
restrictions, qui dépendent des **modules** et des **dates**).

### Fichiers

| Fichier | Rôle |
|---|---|
| `REQUEST.licreq` | Demande d'activation générée par le client (contient l'empreinte) |
| `LICENSE-XXX.lic` | Licence signée générée par l'éditeur |
| `licences_generees.log` | Journal local des licences émises (côté éditeur) |
| `src/main/resources/licence/public.pem` | Clé publique embarquée (vérification) |
| `tools/ubilicense-manager/` | Outil éditeur (clé privée — **hors livraison**) |

### Endpoints REST

| Méthode | Chemin | Accès |
|---|---|---|
| GET | `/api/v1/licence/etat` | Tout utilisateur connecté |
| POST | `/api/v1/licence/importer` | ADMIN / SUPPORT |
| POST | `/api/v1/licence/demande` | ADMIN / SUPPORT |
| GET | `/api/v1/licence/evenements` | ADMIN / SUPPORT |

### Caractéristiques cryptographiques

- Algorithme : **RSA 2048 / SHA-256** (`SHA256withRSA`).
- Format : `base64url(payloadJson) + "." + base64url(signature)`.
- Empreinte serveur : `SRV-` + 8 octets hexadécimaux (SHA-1 de nom d'hôte + MAC).
- Vérification **100 % hors ligne** avec la clé publique embarquée.

---

*Voir aussi : `docs/MODULE_LICENCE.md` (descriptif technique condensé) et
`tools/ubilicense-manager/README.md` (procédure côté éditeur).*
