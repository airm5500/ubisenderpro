# Déploiement & modes de fonctionnement WhatsApp

UbiSenderPro peut envoyer via **deux canaux**, au choix :

| Mode | Canal | Compte Meta | Risque | Pour quoi |
|---|---|---|---|---|
| **API** | WhatsApp **Cloud API** (Meta, officiel) | **requis** | aucun (conforme) | production, conformité, hors fenêtre 24 h (modèles approuvés) |
| **WEB** | **WhatsApp Web** (non officiel, Baileys) | **non requis** | ⚠️ bannissement possible | tests, envoi/extraction sans Meta, volumes modérés à débit lent |

Le mode par défaut se règle dans **Paramètres → Général → Mode d'envoi**. Il
présélectionne le canal dans le composeur « Nouveau message » de la boîte de
réception. Les deux canaux restent utilisables en parallèle.

---

## 1. Pré-requis communs

- **Payara 5**, **MariaDB**, datasource JNDI `UbiSenderProDS` (voir `DEPLOIEMENT.md`).
- Construire le WAR : `mvn clean package` → `target/ubisenderpro.war`.
- Déployer sur Payara. Au 1er démarrage, **Flyway** crée/migre le schéma
  (jusqu'à V12), sauf si `UBISENDERPRO_SKIP_FLYWAY=true` (base installée via
  `db/install/ubisenderpro_install.sql`).
- Compte initial : `admin` / `Admin@2026`.

---

## 2. Mode API (WhatsApp Cloud API)

1. Obtenir auprès de Meta : **Phone Number ID**, **WABA ID**, **Access token**
   (voir la procédure « numéro de test » plus bas si pas encore d'accès).
2. Dans l'app : **Paramètres → Comptes WhatsApp → Nouveau compte**, renseigner
   les identifiants (laisser **Mode test décoché** pour de vrais envois).
3. Webhook (réception + statuts) : exposer publiquement en HTTPS
   `…/ubisenderpro/api/v1/webhooks/whatsapp` et le déclarer dans la console Meta
   (champ `messages`, *verify token* = celui du compte).
4. **Paramètres → Général → Mode = API**.

> Mode **test** (case sur le compte) : simule les envois sans appeler Meta —
> pratique pour valider l'interface sans token.

---

## 3. Mode WEB (WhatsApp Web via service compagnon Docker)

Le canal WEB s'appuie sur un **service Node** (`wa-web/`, Baileys) lancé à côté
de Payara.

### 3.1 Lancer le service (Docker)

```bash
cd wa-web
WA_WEB_TOKEN=un-secret-partage docker compose up -d --build
```

- Écoute sur `:3000`. Sessions persistées dans le volume `wa_web_data`
  (pas de re-scan après redémarrage).
- Vérifier : `curl http://localhost:3000/health` → `{"ok":true}`.

### 3.2 Configurer Payara pour joindre le service

Deux variables, par **variable d'environnement OS** *ou* **propriété JVM** :

| Clé | Valeur | Rôle |
|---|---|---|
| `WA_WEB_URL` | `http://localhost:3000` | URL du service Node |
| `WA_WEB_TOKEN` | `un-secret-partage` | même secret qu'au 3.1 |

**Option A — propriétés JVM (recommandé sur Payara)** :

> ⚠️ `asadmin create-jvm-options` utilise le `:` comme séparateur : il faut
> **échapper les deux-points de l'URL** avec `\:`.

```bash
asadmin create-jvm-options "-DWA_WEB_URL=http\://localhost\:3000"
asadmin create-jvm-options "-DWA_WEB_TOKEN=un-secret-partage"
asadmin restart-domain
# Vérifier :
asadmin list-jvm-options | grep WA_WEB
```

**Option B — variables d'environnement OS (sans échappement)** : `WaWebConfig`
lit d'abord la variable d'environnement, puis la propriété JVM.

- Linux/systemd : exporter `WA_WEB_URL` / `WA_WEB_TOKEN` avant de démarrer Payara.
- Windows : `setx WA_WEB_URL "http://localhost:3000" /M` puis
  `setx WA_WEB_TOKEN "un-secret-partage" /M`, et **redémarrer le service Payara**.

> Si Payara et le service Node sont sur des machines/containers différents,
> remplacer `localhost` par l'hôte joignable (et ouvrir le port 3000 en réseau privé).

### 3.3 Premier scan (connexion)

1. Dans l'app : **WhatsApp Web → Comptes → Nouveau compte** (saisir un libellé).
2. Sélectionner le compte → **« Connecter (QR) »**. Un **QR code** s'affiche.
3. Sur le téléphone : **WhatsApp → Réglages → Appareils connectés →
   Connecter un appareil**, puis scanner le QR.
4. Le statut passe à **CONNECTE**. (La session reste valide après redémarrage.)

### 3.4 Utiliser le canal WEB

- **Envoi en masse** : onglet *Envoi en masse* (5 variantes, pièce jointe
  « Parcourir », débit, liste `numero;nom`).
- **Filtre de numéros** : onglet *Filtre de numéros* (qui a WhatsApp).
- **Extraction** : onglet *Extraction* (contacts, groupes, membres).
- **Composeur** : Discussions → *Nouveau* → choisir le canal **WhatsApp Web**.
- **Paramètres → Général → Mode = WEB** pour en faire le canal par défaut.

> ⚠️ Canal non officiel : garder un **débit lent** (valeurs par défaut : 4–8 s
> entre messages, pause 10–20 s tous les 10) et des **volumes raisonnables**
> pour limiter le risque de bannissement du numéro.

---

## 4. Obtenir un accès Meta de test (mode API)

Sur **developers.facebook.com** : créer une app *Business* → ajouter le produit
**WhatsApp** → la page *API Setup* fournit un **numéro de test**, un
**Phone Number ID**, un **WABA ID** et un **token temporaire (24 h)**, et permet
de déclarer jusqu'à **5 numéros destinataires**. Suffisant pour tout valider.

Si la vérification du compte développeur échoue (SMS non reçu) : essayer la
vérification **par carte**, l'option **« M'appeler »**, ou un compte Facebook
déjà vérifié.

---

## 5. Récapitulatif des variables

| Variable | Côté | Défaut | Rôle |
|---|---|---|---|
| `WA_WEB_URL` | Payara (env ou `-D`) | `http://localhost:3000` | URL du service WhatsApp Web |
| `WA_WEB_TOKEN` | Payara + service Node | *(vide)* | token partagé d'authentification |
| `WA_WEB_DATA` | service Node | `/app/data` | dossier des sessions persistées |
| `UBISENDER_CALLBACK` | service Node | *(vide)* | base UbiSenderPro pour recevoir les **réponses** des clients et l'état de session (ex. `http://localhost:8080/ubisenderpro` ; en Docker Desktop : `http://host.docker.internal:8080/ubisenderpro`). Si vide, pas de réception. |
| `PORT` | service Node | `3000` | port d'écoute |
| `UBISENDERPRO_SKIP_FLYWAY` | Payara | `false` | désactive Flyway si base pré-installée |
