# Proposition — Module « Centre de Support » UbiSenderPro

> Document de cadrage (sans code). Objectif : proposer la meilleure version du
> Centre de Support, **adaptée à l'architecture réelle d'UbiSenderPro**, avec
> phasage, risques, décisions à trancher et recommandation d'intégration.

---

## 1. Ce qui existe déjà et qu'on va réutiliser (ne pas réinventer)

| Besoin du module Support | Brique existante réutilisable |
|---|---|
| Envoi d'e-mail + pièces jointes | `MailService.envoyerAvecPieces(...)` (multipart, déjà livré) |
| Stockage des pièces jointes | `MediaFichierService` (`enregistrer` / `parId`, servi via `/media/{id}`) |
| Paramétrage (e-mail support, seuils…) | `ParametreService` + écran Paramètres (`app.*`) |
| Sécurité / rôles | RBAC `@Secured(menu, action)`, `AuthenticationFilter`, `SessionStore`, `AuthenticatedUser`, `PermissionService` |
| Migrations de schéma | Flyway (auto au `Bootstrap` : `repair()` + `migrate()`) |
| Journalisation d'activité | `AuditService.tracer(...)`, journal de poste (V27) |
| Discussions / conversations | Modèle `Conversation` + `Message` (patron réutilisable pour la « conversation » d'un ticket) |
| Santé WhatsApp / e-mail | `WhatsappAccount` (actif), sessions WhatsApp Web, `MailService.estConfigure()` |
| UI | ExtJS `Usp.*` : grilles, formulaires, `Usp.grilleFiltre`, `Usp.multiPicker`, `Usp.ajax`, permissions `Usp.permBtn` |

➡️ **Conséquence** : le module est en grande partie un **assemblage** de briques déjà présentes. Le vrai travail neuf = la **capture automatique d'erreurs** (dédup par signature) et le **tableau de santé**.

---

## 2. Périmètre proposé (et priorisé)

Sous-menus (dans l'ordre de valeur / faisabilité) :

1. **Me contacter** — formulaire → e-mail support + archivage + accusé. *(réutilise MailService)*
2. **Tickets support** — cycle de vie type ServiceNow, avec conversation. *(patron Conversation/Message)*
3. **Diagnostic & bugs** — journal `usp_application_event`, capture auto + dédup par signature.
4. **Santé de l'application** — tableau de bord d'états et compteurs.
5. **Historique** — vue transverse (demandes + tickets + événements).
6. **Base de connaissances / FAQ** — *évolution* (une FAQ existe déjà côté bot : à mutualiser).

---

## 3. Accès & sécurité — mon désaccord argumenté avec la spec

La spec ChatGPT demande un **Super Administrateur système caché**, absent de la
liste des utilisateurs, avec `GRANT ALL`, seul à voir le Support.

**Mon avis : à ne pas implémenter tel quel** (mauvaise pratique de sécurité et
source de bugs) :
- un compte « invisible » avec tous les droits est un **angle mort d'audit** et un risque (porte dérobée) ;
- UbiSenderPro a **déjà** un rôle `ADMIN` avec bypass total et un modèle de permissions propre.

**Recommandation (2 options, je conseille la A) :**

- **Option A — Rôle système `SUPPORT` (recommandé).** Nouveau menu `support`
  gouverné par le RBAC existant. On crée un **rôle non modifiable** `SUPPORT`
  (comme `ADMIN` est protégé), attribuable uniquement par un ADMIN. Le menu
  Support n'apparaît que pour les porteurs de ce rôle. **Traçable, cohérent avec
  l'existant, zéro compte caché.**
- **Option B — Accès éditeur distant.** Le Support n'est visible que si un
  paramètre `support.editeur_active = true` (poussé par l'éditeur) **et** que
  l'utilisateur est ADMIN. Utile si tu veux que seul l'éditeur (toi) y accède
  chez le client, sans créer de rôle côté client.

> Si tu tiens absolument à un compte « éditeur » masqué de la liste : le faire
> **proprement** = un utilisateur réel portant le rôle `SUPPORT`, avec un
> **flag `systeme = true`** qui l'exclut de la grille des utilisateurs (filtre
> serveur) — mais **il reste dans la base et dans l'audit**. Pas de « GRANT ALL
> magique » hors du modèle de permissions.

---

## 4. Modèle de données proposé (tables `usp_support_*`, additif)

Toutes les tables sont **nouvelles et isolées** (préfixe `usp_support_` / `usp_application_event`), donc **zéro impact** sur l'existant.

### 4.1 Demandes « Me contacter » — `usp_support_demande`
| Champ | Rôle |
|---|---|
| id, created_at | technique |
| nom, societe, email, telephone | contact émetteur |
| objet, corps | message |
| pieces (réf. MediaFichier, CSV d'ids) | pièces jointes réutilisant `usp_media_fichier` |
| statut (ENVOYEE / ARCHIVEE / ECHOUEE), erreur | suivi |
| cree_par | utilisateur connecté |

### 4.2 Tickets — `usp_support_ticket`
| Champ | Rôle |
|---|---|
| id, numero (unique, ex. TCK-2026-0001), created_at, updated_at | technique / référence |
| client_id (nullable), societe, utilisateur (login) | contexte |
| module, type, priorite | classification |
| sujet, description | contenu |
| statut | workflow (voir §5) |
| affecte_a (login) | affectation |
| event_signature (nullable) | lien vers un bug capturé |
| pieces (CSV d'ids media) | pièces jointes |

### 4.3 Conversation d'un ticket — `usp_support_ticket_message`
`id, ticket_id, direction (INTERNE/CLIENT), auteur, corps, pieces, created_at`.
*(Même patron que `Message` d'une conversation WhatsApp.)*

### 4.4 Journal d'événements — `usp_application_event` (le cœur du diagnostic)
| Champ | Rôle |
|---|---|
| id, created_at, last_seen_at | technique |
| module, type (EXCEPTION_JAVA, SQL, JS, API, WHATSAPP, EMAIL, IMPORT…), niveau (INFO/WARN/ERROR/FATAL) | classification |
| signature (hash court) | **clé de dédoublonnage** |
| message_court (tronqué) | lisible |
| occurrences (compteur) | fréquence |
| utilisateur, url_ou_ecran | contexte |
| payload_json | infos techniques+métier (JSON compact) |
| log_ref (chemin fichier) | **stacktrace volumineuse sur disque, pas en base** |
| ticket_id (nullable) | rattachement à un ticket |

> **Bonne idée de la spec conservée** : ne **pas** stocker les stacktraces en base.
> On garde `payload_json` (léger) + une **référence** vers un fichier log sur le
> serveur (rotation/purge configurable).

---

## 5. Workflow des tickets

`NOUVEAU → OUVERT → AFFECTE → EN_COURS → EN_ATTENTE_CLIENT → RESOLU → CLOTURE`
(+ `ANNULE` transverse). Réutiliser le rendu « pastille de statut » déjà employé
partout (couleurs). Chaque changement de statut = une ligne dans la conversation
(traçabilité).

---

## 6. Capture automatique des erreurs — conception (le vrai morceau neuf)

Principe : **un point de collecte unique** `POST /support/events` + plusieurs
**sources** qui l'alimentent, avec **dédoublonnage par signature**.

- **Signature** = hash court de `type + module + message normalisé + 1re ligne de
  stack applicative` (on retire les numéros de ligne volatils, ids, dates).
- **Dédup** : si la signature existe → `occurrences++`, `last_seen_at = now`,
  (option) rattacher au ticket existant. Sinon → nouvel événement.
- **Sources** :
  - **Exceptions Java / SQL** : un `ExceptionMapper` JAX-RS (déjà le point de
    passage des erreurs REST) + interception dans les `catch` sensibles
    (envois WhatsApp/e-mail/import). Faible intrusion.
  - **Erreurs JavaScript** : hook `window.onerror` / `Ext.Error` côté ExtJS →
    `POST /support/events` (throttlé pour éviter le flood).
  - **Erreurs API/WhatsApp/E-mail/Import** : ces services renvoient déjà des
    statuts d'échec (`ECHOUE`, `erreur`) → on émet un événement à ces endroits.
- **Garde-fous** : throttling (max N/min par signature), taille payload plafonnée,
  purge automatique (rétention configurable), **jamais** de données sensibles
  (mots de passe, tokens) dans `payload_json`.

---

## 7. Santé de l'application — conception

Tableau de bord (lecture seule) agrégeant des **sondes** :
- **Base MariaDB** : ping (requête triviale) + latence.
- **WhatsApp Cloud** : au moins un `WhatsappAccount` actif + (option) appel léger
  de vérification du token.
- **WhatsApp Web** : état des sessions (déjà suivi).
- **E-mail SMTP** : `MailService.estConfigure()` (+ test d'envoi à la demande).
- **Serveur** : uptime appli, mémoire JVM, heure serveur.
- **Compteurs 24 h / 7 j** : nb d'événements par niveau, tickets ouverts,
  derniers incidents (depuis `usp_application_event`).

Exposé par `GET /support/sante` (agrégat calculé à la demande, pas de nouvelle
table nécessaire).

---

## 8. Intégration UI (ExtJS)

- Nouveau menu `Usp.MENU` : `{ text:'Support', view:'support', icon:'🛟', roles:['SUPPORT'] (ou ADMIN selon option) }`.
- Un `tabpanel` `Usp.support.panel()` avec les sous-onglets du §2.
- Réutilise `Usp.grilleFiltre` (recherche/période/statut), `Usp.multiPicker`,
  `Usp.parColonne`, les pièces jointes (upload `/media/upload`).
- Tickets : grille + fenêtre détail (infos + conversation + pièces), même ergonomie
  que les Discussions.

---

## 9. Phasage recommandé (lots livrables, testables un par un)

- **Lot S1 — Me contacter** : formulaire + e-mail (MailService) + archivage +
  accusé. *(rapide, valeur immédiate, risque faible)*
- **Lot S2 — Journal d'événements + capture** : table `usp_application_event`,
  endpoint de collecte, `ExceptionMapper`, hook JS, dédup par signature.
- **Lot S3 — Tickets** : CRUD + workflow + conversation + pièces + rattachement
  d'un événement à un ticket.
- **Lot S4 — Santé** : sondes + tableau de bord.
- **Lot S5 — Historique + Base de connaissances (FAQ)** : vue transverse + FAQ
  (mutualiser avec la FAQ du bot existante).

---

## 10. Risques & points de vigilance

- **Flood d'événements** (une erreur en boucle) → throttling + dédup obligatoires
  dès le Lot S2.
- **Confidentialité** : le `payload_json` ne doit jamais contenir de secrets ni de
  données patients/clients sensibles → liste blanche des champs capturés.
- **Volumétrie logs** : rotation + rétention configurable ; les gros logs sur
  disque, pas en base.
- **Compte « super admin » caché** : voir §3 — **écarté** au profit d'un rôle
  système traçable.
- **Auto-capture intrusive** : commencer par les points de passage existants
  (ExceptionMapper, catch d'envoi) plutôt que d'instrumenter tout le code.

---

## 11. Décisions à trancher (avant de coder)

1. **Accès Support** : rôle système `SUPPORT` (option A, recommandé) **ou** accès
   éditeur par paramètre (option B) ?
2. **Destinataire « Me contacter »** : une adresse `support.email` paramétrable (=
   toi, l'éditeur) — confirmer.
3. **Tickets** : usage **interne** (utilisateurs du client ouvrent des tickets à
   l'éditeur) ou **uniquement éditeur** ? Cela change qui voit quoi.
4. **Rétention** des événements/logs (ex. 90 jours) et emplacement disque des logs.
5. **Base de connaissances** : réutiliser la FAQ du bot existante ou table dédiée ?

---

## 12. Mon avis (synthèse)

- **Très pertinent et réaliste** : ~70 % s'appuie sur des briques déjà livrées
  (e-mail+pièces jointes, médias, RBAC, Flyway, conversations). Le module apporte
  une vraie valeur d'exploitation.
- **À corriger vs la spec** : abandonner le « super admin caché GRANT ALL » →
  **rôle système `SUPPORT`** dans le RBAC existant (traçable, sûr).
- **Ordre conseillé** : livrer d'abord **Me contacter** (quick win) puis le
  **journal d'événements + capture** (le plus utile pour toi en exploitation),
  ensuite **Tickets** et **Santé**.
- **Intégration** : **directement dans UbiSenderPro** comme nouveau menu isolé
  (tables `usp_support_*`), additif et sans risque pour les modules existants —
  cohérent avec la façon dont on a intégré le Recouvrement.
- **Lien avec les Licences** : le Support est le **canal naturel** pour gérer les
  demandes de renouvellement de licence (voir l'autre document) — les deux modules
  se complètent.
