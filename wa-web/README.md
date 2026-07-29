# UbiSenderPro — service compagnon WhatsApp Web

Canal **non officiel** (Baileys / WhatsApp Web) utilisé par UbiSenderPro pour :
connexion par **QR**, envoi **texte/média**, **filtre de numéros**.

> ⚠️ Contraire aux CGU de WhatsApp → **risque de bannissement** du numéro.
> À réserver à des numéros assumés. Utiliser un **débit lent** (réglages d'envoi).

## Lancer (Windows — recommandé)

1. **Configurer une seule fois** : copier `.env.example` en `.env` et vérifier
   les valeurs (surtout `UBISENDER_CALLBACK`).
   ```cmd
   copy .env.example .env
   ```
2. **Démarrer** : double-cliquer sur **`demarrer.bat`** (il installe les
   dépendances au premier lancement, puis démarre le service).

> ⚠️ **Piège classique à éviter :** un `set UBISENDER_CALLBACK=...` en invite de
> commandes ne vaut **que pour la fenêtre en cours**. En rouvrant une invite, la
> variable est perdue : le service reçoit alors les messages mais **ne les
> transmet plus** à l'application — les réponses des clients n'apparaissent pas
> dans les Discussions. Le fichier `.env` supprime définitivement ce problème.
> Au démarrage, le service **affiche en clair** si la configuration manque.

### Variables de configuration

| Variable | Obligatoire | Rôle |
|---|---|---|
| `UBISENDER_CALLBACK` | **oui** | URL de l'application (ex. `http://localhost:8080/ubisenderpro`). Sans elle, **aucun message entrant n'est remonté** |
| `WA_WEB_TOKEN` | oui | Jeton partagé, **identique** côté application |
| `PORT` | non | Port d'écoute (défaut `3000`) |
| `WA_WEB_DATA` | non | Dossier des sessions (défaut `./data`) |
| `LOG_LEVEL` | non | `info` (défaut), `debug`, `warn`, `error` |

## Lancer (Docker)

```bash
cd wa-web
WA_WEB_TOKEN=un-secret-partage docker compose up -d --build
```

Le service écoute sur `:3000`. Les sessions sont persistées dans le volume
`wa_web_data` (pas besoin de re-scanner après redémarrage).

## Configurer le backend Java (Payara)

Définir ces variables d'environnement côté Payara :

| Variable | Rôle | Exemple |
|---|---|---|
| `WA_WEB_URL` | URL du service Node | `http://localhost:3000` |
| `WA_WEB_TOKEN` | même secret que ci-dessus | `un-secret-partage` |

## API (interne, en-tête `X-Api-Token`)

| Méthode | Route | Corps |
|---|---|---|
| POST | `/sessions/:id/start` | — |
| GET | `/sessions/:id/status` | — |
| POST | `/sessions/:id/logout` | — |
| POST | `/sessions/:id/send` | `{to, text}` |
| POST | `/sessions/:id/send-media` | `{to, type, mediaUrl|mediaBase64, mimeType, fileName, caption}` |
| POST | `/sessions/:id/check-numbers` | `{numbers:[...]}` |

Statuts : `DECONNECTE` · `CONNEXION` · `QR` · `CONNECTE`.

**Santé de réception** (`health`, indépendante du statut) : `OK` · `DEGRADED`.
Après une longue coupure, la session de chiffrement peut se désynchroniser : le
socket reste « ouvert » (l'envoi fonctionne) mais les messages entrants
arrivent **illisibles** et les réponses des clients se perdent silencieusement.
Le service détecte ces entrants non déchiffrables, bascule la session en
`DEGRADED` et le signale à UbiSmartCRM Pro via le callback `/status`
(`{status, health, reason}`). L'application affiche alors une bannière
« session à reconnecter » avec accès direct au QR. Une réception lisible (ou un
rescan du QR) rétablit `OK`. Le champ `lastInboundAt` horodate la dernière
réception saine (exposé par `GET /sessions/:id/status`).
