# Module « Gestion des Licences » — Descriptif & Manuel de mise en place

> UbiSmartCRM Pro · v2.0 — menu **🔑 Licence** + outil éditeur **UbiLicense Manager**

---

## 1. Descriptif du module

### 1.1 Principe

- Une licence est une **charge utile JSON signée** (RSA 2048 / SHA-256) par
  l'éditeur. L'application **ne génère jamais** de licence : elle **vérifie**
  la signature avec la **clé publique embarquée** dans le WAR
  (`src/main/resources/licence/public.pem`).
- La **clé privée** reste chez l'éditeur, dans l'outil séparé
  **UbiLicense Manager** (`tools/ubilicense-manager`, hors WAR).
- ➡️ Une licence est **infalsifiable** : modifier un seul caractère (par ex.
  la date d'expiration) invalide la signature (couvert par test automatique).

### 1.2 Contenu d'une licence

Identifiant client, société, pays, e-mail, **type** (ESSAI / STANDARD / PRO /
ENTREPRISE), **dates** d'activation et d'expiration, **limites** (utilisateurs,
agences), **modules activés**, versions min/max couvertes, **empreinte
serveur** (optionnelle) — le tout signé.

Deux formats équivalents : **clé d'activation** (chaîne compacte
`base64(payload).base64(signature)`) ou fichier **`.lic`**.

### 1.3 Application des restrictions

| Mécanisme | Effet |
|---|---|
| **Modules** | Droits effectifs = **permissions RBAC ∩ modules licenciés**. Un menu non licencié disparaît ET ses endpoints sont refusés côté serveur. Menus « socle » jamais filtrés : tableau de bord, Paramètres, Utilisateurs, Support, Licence. |
| **Expiration** | À J-30 → statut « Expire bientôt » + bandeau orange. Après l'échéance → **période de grâce** (`licence.grace_jours`, défaut 7 j, bandeau orange). Grâce dépassée → **blocage** des envois (campagnes, WhatsApp Web, relances), des **automatisations** (ordonnanceurs suspendus) et des **imports**. La **consultation des données**, l'administration, l'écran Licence et le rôle SUPPORT restent accessibles. |
| **Empreinte serveur** | Si présente dans la licence, elle doit correspondre à celle du serveur (`SRV-XXXXXXXX`, dérivée hôte + carte réseau). Tolérante : en cas de changement de machine, une **réémission** suffit (pas de blocage sournois). |
| **Anti-recul d'horloge** | L'application mémorise la plus grande date vue ; un recul d'horloge > 24 h passe la licence en statut « Horloge » (restrictions actives) jusqu'à correction. |
| **Version min/max** | Une licence n'est acceptée que si la version de l'application est couverte. |

### 1.4 Le point clé : `licence.obligatoire`

| Valeur | Comportement |
|---|---|
| `false` *(défaut)* | **Aucune restriction.** L'application fonctionne comme avant, avec ou sans licence (zéro régression pour un déploiement existant). |
| `true` | Le régime de licence s'applique : modules filtrés, blocages post-expiration, blocage des envois si aucune licence n'est installée. |

### 1.5 Statuts possibles

`AUCUNE` · `ACTIVE` · `EXPIRE_BIENTOT` (≤ 30 j) · `GRACE` · `EXPIREE` ·
`INVALIDE` (signature/empreinte/version) · `HORLOGE` (recul détecté).

### 1.6 Données

Tables **V55** : `usp_licence` (état courant), `usp_licence_evenement`
(journal : activation, renouvellement, refus, demandes). Additif, isolé.

---

## 2. Manuel de mise en place (éditeur) — pas à pas

### Étape 0 — Construire l'outil éditeur

```bash
mvn -f tools/ubilicense-manager/pom.xml package
java -jar tools/ubilicense-manager/target/ubilicense-manager.jar
```

> ⚠️ UbiLicense Manager détient la clé privée : **ne jamais le livrer** au
> client, ne jamais l'inclure dans le WAR.

### Étape 1 — Générer VOS clés de production (une seule fois)

1. UbiLicense Manager → onglet **🗝️ Clés** → *Générer une nouvelle paire* →
   choisir un dossier **hors dépôt Git**.
2. `private.pem` : à conserver **en lieu sûr** (coffre, support chiffré,
   sauvegardé). Sa **fuite** compromet tout le système ; sa **perte** empêche
   d'émettre de nouvelles licences.
3. `public.pem` : copier dans l'application →
   `src/main/resources/licence/public.pem`, puis **rebuilder le WAR**.

> La paire `private_DEV.pem` fournie dans le dépôt sert uniquement aux
> **tests** (elle est appariée à la clé publique embarquée par défaut).
> **Ne pas l'utiliser en production.**

### Étape 2 — Déployer chez le client

1. Déployer le WAR (la migration **V55** crée les tables et paramètres).
2. **Paramètres** : passer **`licence.obligatoire` = `true`**.
3. (Optionnel) Ajuster **`licence.grace_jours`** (défaut 7).

### Étape 3 — Activation hors ligne (flux recommandé, adapté aux sites cloisonnés)

| # | Qui | Action |
|---|---|---|
| 1 | **Client** (ADMIN) | Menu **Licence → 📤 Générer une demande (.licreq)** → le fichier `REQUEST.licreq` se télécharge (il contient identité + **empreinte du serveur**). L'envoyer à l'éditeur (e-mail, clé USB…). |
| 2 | **Éditeur** | UbiLicense Manager → onglet **🔑 Licence** → **Charger .licreq…** (remplit l'empreinte, la société, l'e-mail) → compléter type, dates, limites, **modules cochés** → **🔏 Générer la licence** → fichier `LICENSE-XXX.lic` (la clé d'activation équivalente s'affiche aussi). |
| 3 | **Client** (ADMIN) | Menu **Licence → 📥 Importer une licence** → coller la clé **ou** charger le `.lic` → **Activer**. La signature est vérifiée localement, l'état passe à **Active**. |

*Activation en ligne : non incluse dans cette version (lot L4 optionnel) — le
hors ligne couvre tous les cas.*

### Étape 4 — Vérifier

- **Licence → État** : statut **Active**, échéance, modules, empreinte.
- Se connecter avec un utilisateur : les menus **hors modules licenciés ont
  disparu** ; un appel direct à leurs API renvoie 403.
- **Journal** : l'activation est tracée.

---

## 3. Exploitation courante

### 3.1 Alertes d'échéance

Bandeau global automatique (tous les utilisateurs) : **orange** à partir de
J-30 puis en période de grâce, **rouge** après expiration / licence invalide /
horloge suspecte — avec lien direct vers l'écran Licence. Revérification
automatique toutes les heures.

### 3.2 Renouvellement

L'éditeur génère une **nouvelle licence** (mêmes étapes 2-3, nouvelles dates) →
le client **importe** → prise d'effet immédiate. **Aucune réinstallation.**

### 3.3 Transfert de serveur (changement de machine)

1. Sur le **nouveau** serveur : générer une nouvelle demande `.licreq`
   (nouvelle empreinte).
2. L'éditeur émet une **nouvelle licence** avec cette empreinte.
3. Import sur le nouveau serveur. *(L'ancienne licence devient inutilisable
   ailleurs : l'empreinte ne correspondra pas.)*

### 3.4 Que se passe-t-il à l'expiration ?

| Moment | Effet |
|---|---|
| J-30 → échéance | Bandeau orange « expire bientôt » (J-30/15/7/1 visibles via le compteur de jours) |
| Échéance → + grâce | Bandeau orange « période de grâce » — **tout fonctionne encore** |
| Après la grâce | **Envois, automatisations, imports bloqués** (message explicite). Consultation, administration, écrans Licence & Support **toujours accessibles** pour renouveler |

### 3.5 Mode maintenance

Le rôle **SUPPORT** (module Centre de support) reste pleinement fonctionnel
même licence expirée : diagnostic, tickets et **renouvellement de licence** —
c'est le mode maintenance officiel, traçable, sans compte caché.

---

## 4. Référence rapide

### Paramètres

| Clé | Défaut | Rôle |
|---|---|---|
| `licence.obligatoire` | `false` | `true` = régime de licence actif (modules + blocages) |
| `licence.grace_jours` | `7` | Période de grâce après expiration |

### Modules licenciables (codes)

`inbox, clients, catalogue, promotions, marketing, dispo, infos, campaigns,
waweb, historique, crm, recouvrement` — liste vide dans la licence = **tous**.

### Fichiers

| Fichier | Rôle |
|---|---|
| `REQUEST.licreq` | Demande d'activation (générée par le client, contient l'empreinte) |
| `LICENSE-XXX.lic` | Licence signée (générée par l'éditeur) |
| `src/main/resources/licence/public.pem` | Clé publique embarquée (vérification) |
| `tools/ubilicense-manager/` | Outil éditeur (clé privée — hors livraison) |

### Endpoints

| Méthode | Chemin | Accès |
|---|---|---|
| GET | `/licence/etat` | Tout utilisateur connecté |
| POST | `/licence/importer` | ADMIN / SUPPORT |
| POST | `/licence/demande` | ADMIN / SUPPORT |
| GET | `/licence/evenements` | ADMIN / SUPPORT |
