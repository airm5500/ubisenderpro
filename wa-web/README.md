# UbiSenderPro — service compagnon WhatsApp Web

Canal **non officiel** (Baileys / WhatsApp Web) utilisé par UbiSenderPro pour :
connexion par **QR**, envoi **texte/média**, **filtre de numéros**.

> ⚠️ Contraire aux CGU de WhatsApp → **risque de bannissement** du numéro.
> À réserver à des numéros assumés. Utiliser un **débit lent** (réglages d'envoi).

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
