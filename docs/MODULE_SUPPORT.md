# Module « Centre de Support » — Descriptif & Manuel d'utilisation

> UbiSmartCRM Pro · v2.0 — menu **🛟 Centre de support**

---

## 1. Descriptif du module

Le Centre de support centralise la relation entre les **utilisateurs de
l'application** et l'**éditeur** : demandes de contact, tickets d'assistance
avec conversation, capture automatique des erreurs (diagnostic), surveillance
de la santé de l'application et FAQ.

### 1.1 Fonctionnalités

| Onglet | Pour qui | Rôle |
|---|---|---|
| 📨 **Me contacter** | Tous les utilisateurs | Envoyer une demande à l'éditeur (e-mail + archivage + accusé de réception) |
| 🎫 **Mes tickets** | Tous les utilisateurs | Ouvrir un ticket et suivre sa conversation |
| 🎟️ **Tous les tickets** | ADMIN / SUPPORT | Traiter tous les tickets (statut, affectation, réponse) |
| 📥 **Demandes reçues** | ADMIN / SUPPORT | Archive des demandes « Me contacter » |
| 🐞 **Diagnostic & bugs** | ADMIN / SUPPORT | Journal des erreurs capturées automatiquement (dédoublonnées) |
| ❤️ **Santé** | ADMIN / SUPPORT | Sondes : base, WhatsApp API/Web, e-mail, serveur, incidents |
| 📚 **FAQ** | Tous les utilisateurs | Base de connaissances (partagée avec le bot) |

### 1.2 Modèle d'accès (sécurité)

- Le module s'appuie sur le **RBAC existant** (menu `support`). Tous les rôles
  reçoivent `VOIR` + `CREER` (contacter / ouvrir un ticket).
- Un **rôle système `SUPPORT`** donne l'accès complet (traitement des tickets,
  diagnostic, santé). Il est attribuable par un ADMIN, comme tout rôle.
- Un **compte éditeur `support`** est créé automatiquement, **masqué de la
  grille Utilisateurs** (indicateur `systeme`) mais **présent en base et dans
  l'audit** — pas de porte dérobée invisible.
  - Login : `support`
  - Mot de passe initial : paramètre `support.mot_de_passe_initial`
    (haché au premier démarrage, **à changer après la première connexion**).
- Les endpoints sensibles sont protégés **côté serveur**
  (`@Secured(roles = {ADMIN, SUPPORT})`), pas seulement masqués à l'écran.

### 1.3 Capture automatique des erreurs (diagnostic)

- **Sources** : exceptions Java et violations SQL (interceptées au point de
  passage REST), erreurs JavaScript de l'interface (hook `window.onerror`).
- **Dédoublonnage par signature** : une même erreur répétée incrémente un
  compteur (`occurrences`) au lieu de remplir la base. La signature est un hash
  du type + module + message normalisé (numéros/dates volatils retirés).
- **Garde-fous** : throttling (10/min par signature côté serveur, 5/min côté
  navigateur), taille de payload plafonnée, purge automatique selon la
  rétention (`support.retention_jours`, défaut **90 jours**).
- La capture est **best-effort** : elle ne peut jamais faire échouer
  l'opération métier ni bloquer l'utilisateur.

### 1.4 Données (tables isolées, additives)

`usp_support_demande`, `usp_support_ticket`, `usp_support_ticket_message`,
`usp_application_event` — préfixe dédié, **aucun impact** sur les tables
existantes. Migration **V54**.

---

## 2. Manuel d'utilisation

### 2.1 Paramétrage initial (ADMIN — une fois)

1. **Paramètres → Général** : renseigner **`support.email`** = l'adresse
   e-mail de l'éditeur (destinataire des demandes « Me contacter »).
   *Sans cette adresse, les demandes sont archivées mais pas envoyées.*
2. Vérifier que le **SMTP** est configuré (paramètres `mail.smtp.*`) — sinon
   ni l'e-mail au support ni l'accusé ne partent (archivage seul).
3. (Optionnel) Ajuster **`support.retention_jours`** (durée de conservation du
   journal d'erreurs, défaut 90).
4. Changer le mot de passe du compte **`support`** après sa première connexion.

### 2.2 Contacter le support (tout utilisateur)

1. Menu **Centre de support → 📨 Me contacter**.
2. Vérifier nom / société / e-mail de réponse (pré-remplis), saisir **Objet**
   et **Votre demande** (obligatoires), joindre au besoin une capture ou un PDF.
3. **📨 Envoyer au support** :
   - la demande est **archivée** dans l'application ;
   - un **e-mail** part vers l'éditeur, avec les pièces jointes ;
   - un **accusé de réception** est envoyé à votre adresse.

### 2.3 Ouvrir et suivre un ticket (tout utilisateur)

1. **🎫 Mes tickets → ➕ Nouveau ticket** : sujet, type (Incident / Question /
   Demande d'évolution / Bug), priorité, module concerné, description, pièce.
2. Le ticket reçoit un numéro **TCK-AAAA-NNNN** ; votre description devient le
   premier message de la conversation.
3. **Double-clic** sur un ticket : fiche détaillée + **conversation** (vos
   messages en vert, les réponses du support en bleu, les changements de
   statut en lignes système). Répondre via la zone du bas.

### 2.4 Traiter les tickets (ADMIN / SUPPORT)

1. **🎟️ Tous les tickets** : filtrer par statut, rechercher (n°, sujet).
2. Double-clic → fiche : **« Changer le statut… »** (workflow :
   `NOUVEAU → OUVERT → AFFECTÉ → EN_COURS → EN_ATTENTE_CLIENT → RÉSOLU →
   CLÔTURÉ`, + `ANNULÉ`) et **« 👤 M'affecter »**. Chaque changement est tracé
   dans la conversation.
3. Répondre dans la conversation (vos messages sont marqués « INTERNE »).

### 2.5 Diagnostic & bugs (ADMIN / SUPPORT)

1. **🐞 Diagnostic & bugs** : chaque ligne = une erreur unique, avec niveau,
   type (EXCEPTION_JAVA / SQL / JS…), module, message, **nombre
   d'occurrences** et dernière survenue.
2. **👁️** : détail complet (signature, écran/URL, extrait technique).
3. **🎫➕** : créer un **ticket BUG** pré-rempli depuis l'événement (les deux
   sont liés).
4. **🧹 Purger** : supprime les événements plus vieux que la rétention.

### 2.6 Santé de l'application (ADMIN / SUPPORT)

**❤️ Santé** affiche des cartes vertes/rouges : base de données (latence),
WhatsApp API (comptes actifs), WhatsApp Web (sessions connectées), e-mail
(SMTP configuré), serveur (durée de fonctionnement, mémoire) et compteurs
d'incidents (erreurs 24 h / 7 j, tickets ouverts). **🔄 Relancer les sondes**
pour actualiser.

### 2.7 FAQ

**📚 FAQ** liste les questions/réponses actives. Le contenu est **partagé avec
le bot** et s'édite dans **Paramètres → Bot** (une seule source de vérité).

---

## 3. Référence rapide des paramètres

| Clé | Défaut | Rôle |
|---|---|---|
| `support.email` | *(vide)* | Destinataire des demandes « Me contacter » |
| `support.retention_jours` | `90` | Rétention du journal d'erreurs |
| `support.mot_de_passe_initial` | `Support@2026` | Mot de passe initial du compte `support` (haché au 1er démarrage) |
