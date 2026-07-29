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

### « En attente de ce message. Ceci pourrait prendre un moment »

Message affiché **sur le téléphone du destinataire** : il a bien reçu le
message mais n'a pas pu le **déchiffrer**, et il en redemande une copie
(*retry receipt*). Le service la renvoie automatiquement — d'où le délai avant
l'affichage.

Causes fréquentes, par ordre d'importance :

1. **Ré-appairage récent** (déconnexion + nouveau scan du QR). Chaque nouveau
   scan crée un nouvel appareil (`:18`, `:19`, `:20`…) et **réinitialise les
   sessions de chiffrement avec tous les contacts** : les premiers messages
   vers chaque contact demandent alors une reprise. C'est le facteur n°1 —
   évitez de rescanner sans nécessité, cela se stabilise ensuite.
2. **Arrêt brutal du service** (fenêtre fermée d'un coup) : l'état de
   chiffrement peut rester à demi écrit. **Arrêtez toujours avec Ctrl+C.**
3. Premier échange avec un contact, ou contact resté longtemps sans échange.

Ce que fait le service pour limiter le problème : cache mémoire des clés
Signal, réponse automatique aux *retry receipts*, **cache des messages envoyés
persisté sur disque** (`messages-envoyes.json`, 300 derniers) — donc une reprise
reste possible même après un redémarrage — et **arrêt propre** sur Ctrl+C.

Le journal trace chaque demande de renvoi (`Demande de renvoi : message
retrouvé…` / `INTROUVABLE`) : c'est le moyen de vérifier que le mécanisme
fonctionne.

### Version de Baileys : épinglée volontairement

`package.json` fige **`6.17.16`** (sans `^`). Ce n'est pas un oubli : l'API
change entre versions mineures de la branche 6.x — en 6.7.x l'export `default`
**est** la fabrique de connexion, en 6.17.x c'est un **objet** qui la contient.
Une plage `^6.x` pouvait donc faire planter le service au démarrage selon le
moment de l'installation. (Le code retient désormais le premier candidat
appelable, mais l'épinglage garantit une installation reproductible.)

L'adressage **`@lid`** de WhatsApp — visible dans les journaux à la place du
numéro (`@s.whatsapp.net`) — est la cause classique des « En attente de ce
message » : sa prise en charge s'est nettement améliorée au fil des versions
6.x. `demarrer.bat` compare la version installée à celle de `package.json` et
relance `npm install` automatiquement en cas d'écart.

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
