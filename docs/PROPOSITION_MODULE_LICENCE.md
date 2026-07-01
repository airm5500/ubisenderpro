# Proposition — Gestion des Licences UbiSenderPro

> Document de cadrage (sans code). Objectif : proposer un système de licence
> **fiable, simple, sécurisé contre la falsification, en ligne comme hors ligne**,
> adapté à l'architecture réelle d'UbiSenderPro, avec phasage, risques, décisions
> et recommandation d'intégration.

---

## 1. Contexte & question préalable (importante)

Aujourd'hui UbiSenderPro est déployé pour **un exploitant** (distributeur pharma).
Un module de licence n'a de sens que si l'objectif est de **commercialiser** le
logiciel à **plusieurs clients** (multi-déploiements on-premise). 

➡️ **À confirmer** : cette gestion de licences vise-t-elle la **revente** du
produit à d'autres distributeurs ? Si oui, la logique proposée ci-dessous est la
bonne. Si non (mono-client), on peut se limiter à un simple **verrou d'échéance**
(beaucoup plus léger) — je le signale en §11.

Le reste du document suppose l'objectif **commercialisation modulaire**.

---

## 2. Principe directeur (validé de la spec)

- La **génération** de licences n'est **jamais** faite par UbiSenderPro.
- Un outil éditeur séparé — **UbiLicense Manager** — crée / renouvelle / suspend /
  transfère les licences.
- **Signature asymétrique** : clé **privée** chez l'éditeur uniquement ; clé
  **publique** embarquée dans UbiSenderPro. L'appli **vérifie** seulement la
  signature → **infalsifiable sans la clé privée**. ✅ Excellent choix, on garde.

---

## 3. Deux livrables distincts

| Livrable | Nature | Qui l'utilise |
|---|---|---|
| **UbiLicense Manager** | Outil **séparé** de l'éditeur (appli de bureau ou petit web interne) | Toi (éditeur) |
| **Client de licence** intégré à UbiSenderPro | Vérification signature + activation + garde-fous + menu Licence | Le client + l'éditeur |

> UbiLicense Manager **n'est pas** dans le WAR d'UbiSenderPro (il détient la clé
> privée — il doit rester hors du produit livré). C'est un projet à part.

---

## 4. Contenu d'une licence (charge utile signée)

Champs de la licence (JSON compact signé) :

- Identifiant client, société, pays, e-mail principal
- Type de licence (ESSAI / STANDARD / PRO / ENTREPRISE…)
- Dates : activation, expiration
- Limites : nb max d'utilisateurs, nb max d'agences
- **Modules activés** (voir §5)
- Version minimale / maximale autorisée
- Empreinte serveur (optionnelle, voir §8)
- **Signature numérique** (sur tous les champs ci-dessus)

Formats de distribution : **clé d'activation** (chaîne compacte) **ou** fichier
`ubisenderpro.lic`.

---

## 5. Modules activables — les mapper au RBAC existant

Les modules de licence doivent correspondre aux **menus** déjà gérés par le RBAC :
`inbox, clients, catalogue, promotions, marketing, dispo, infos, campaigns, waweb,
historique, crm, recouvrement, users, settings` (+ `support` à venir), et des
**capacités** transverses : `SMS, WHATSAPP, EMAIL, MULTI_AGENCES, API`.

**Mécanisme d'application (clé de l'intégration) :**
- La licence définit l'ensemble des modules **autorisés**.
- Les droits **effectifs** d'un utilisateur = **permissions RBAC ∩ modules licenciés**.
- Concrètement : au calcul des menus/permissions (`PermissionService` /
  `/permissions/me`), on **filtre** par les modules de la licence. Un menu non
  licencié n'apparaît pas et ses endpoints sont refusés.

➡️ Élégant : **on ne double pas** la logique de droits, on **intersecte** avec la
licence. Additif et cohérent.

---

## 6. Modèle de données (côté UbiSenderPro, minimal)

On stocke l'**état de licence courant**, pas un catalogue (le catalogue est côté
UbiLicense Manager).

- **Table `usp_licence`** (une ligne active) : `id, client_id, societe, pays,
  email, type, date_activation, date_expiration, max_users, max_agences,
  modules (CSV), version_min, version_max, empreinte_serveur (nullable),
  signature, statut (ACTIVE/EXPIREE/SUSPENDUE/GRACE), importee_le`.
- **Table `usp_licence_evenement`** (audit local) : activations, renouvellements,
  transferts, passages en grâce/expiration.

> La **clé publique** est embarquée dans l'application (ressource / paramètre non
> modifiable côté client), pas en base modifiable.

---

## 7. Activation

**En ligne** (si serveur de licences éditeur disponible) :
1. Saisie de la clé → l'appli appelle le serveur éditeur → validation → activation.

**Hors ligne** (environnements isolés — courant en pharma) :
1. Le client génère une **demande** `REQUEST.licreq` (contient l'empreinte serveur
   + l'identité client).
2. L'éditeur ouvre la demande dans UbiLicense Manager → génère `LICENSE.lic`.
3. Le client **importe** `LICENSE.lic` → l'appli **vérifie la signature** (clé
   publique) et active.

> Le mode **hors ligne** est le socle indispensable (le mode en ligne est un
> confort). On le priorise.

---

## 8. Anti-fraude & robustesse

- **Signature** obligatoire sur toute la charge utile (déjà prévu).
- **Empreinte serveur** (optionnelle mais recommandée) : dérivée d'éléments stables
  (ex. identifiant machine + nom d'hôte). Empêche de copier la licence sur un autre
  serveur. **Attention** : trop stricte = blocages lors de maintenances hardware →
  la rendre **tolérante** (ex. re-délivrance facile via transfert, cf. §10).
- **Anti-recul d'horloge** : mémoriser la **dernière date vue** (horodatage
  monotone en base) ; si l'horloge système recule brutalement sous cette date →
  suspicion → passage en mode restreint jusqu'à vérification. Évite le contournement
  par changement de date.
- **Version min/max** : bloque l'usage d'une licence sur une version non couverte.

---

## 9. Expiration, alertes & période de grâce

- **Alertes** proactives : J-30, J-15, J-7, J-1 (bandeau dans l'appli + option
  e-mail via `MailService`).
- **Période de grâce** configurable (ex. 7 jours) après expiration.
- **Après expiration (grâce dépassée)** :
  - accès **administrateur** maintenu, **consultation** des données autorisée ;
  - **blocage des fonctions sous licence** : campagnes, envois WhatsApp/e-mail,
    automatisations, imports de masse… (les envois sont le cœur « facturable ») ;
  - **menu Licence** accessible pour renouveler ;
  - le module **Support / maintenance** reste accessible (diagnostic + renouvellement).

---

## 10. Renouvellement & transfert

- **Renouvellement** : nouvelle clé **ou** nouveau fichier `.lic` → import → mise à
  jour de `usp_licence`. **Aucune réinstallation.**
- **Transfert** (changement de serveur) : l'éditeur **invalide** l'ancienne
  activation et **génère** une nouvelle licence (nouvelle empreinte). Procédure
  simple pour absorber les changements d'infrastructure.

---

## 11. Mode Maintenance / accès éditeur (à réconcilier avec le Support)

La spec demande un « Super Administrateur système » accessible même licence
expirée. **Même position que pour le Support** : pas de compte caché « GRANT ALL ».

**Recommandation** : le **rôle système `SUPPORT`** (défini dans la proposition
Support) reste **actif même hors licence** — il permet le diagnostic et le
renouvellement. C'est le **mode maintenance**, sans porte dérobée invisible.

> **Alternative légère (si mono-client, pas de revente)** : oublier tout ce
> dispositif asymétrique et ne garder qu'un **verrou d'échéance** simple
> (paramètre `licence.expiration` + bandeau + blocage des envois). À décider en §1.

---

## 12. Phasage recommandé (lots)

- **Lot L0 — Décision & clés** : confirmer l'objectif (revente ?), générer la paire
  de clés, définir le format de licence.
- **Lot L1 — Client de licence (hors ligne)** : import `.lic`, vérification
  signature, table `usp_licence`, menu **Licence** (état + import + alertes).
- **Lot L2 — Application des modules** : intersection RBAC ∩ modules licenciés
  (filtrage des menus/permissions), blocage post-expiration + grâce.
- **Lot L3 — UbiLicense Manager (éditeur, projet séparé)** : génération/signature,
  renouvellement, suspension, transfert, historique.
- **Lot L4 — En ligne + portail éditeur** : serveur de licences, activation en
  ligne, portail de suivi (expirations, modules actifs).

---

## 13. Risques & points de vigilance

- **Empreinte serveur trop stricte** → blocages en production (maintenance HW).
  La rendre tolérante + transfert facile.
- **Blocage post-expiration trop large** → risque de couper l'accès aux données du
  client. **Ne bloquer que les fonctions sous licence** (envois/automatisations),
  jamais la consultation.
- **Sécurité de la clé privée** (côté éditeur) : sa fuite compromet tout le
  système. Stockage hors ligne, sauvegardé, jamais dans un dépôt.
- **Support hors ligne obligatoire** : ne pas dépendre d'un serveur de licences
  pour fonctionner (les sites pharma sont souvent cloisonnés).
- **Complexité vs besoin réel** : si mono-client, tout ceci est surdimensionné (§11).

---

## 14. Décisions à trancher (avant de coder)

1. **Objectif** : revente multi-clients (système complet) **ou** mono-client
   (verrou d'échéance simple) ?
2. **Empreinte serveur** : activée ou non (verrouillage matériel) ?
3. **Granularité des modules** : quels menus/capacités sont réellement « à vendre »
   séparément ?
4. **Politique post-expiration** : quelles fonctions exactement sont bloquées ?
   (proposition : tous les **envois** + **automatisations** + **imports de masse**).
5. **UbiLicense Manager** : appli de bureau (Java) ou petit web interne éditeur ?

---

## 15. Mon avis (synthèse)

- **Architecture saine** : signature asymétrique + séparation éditeur/produit +
  hors ligne = **le bon design** pour du logiciel on-premise. Je valide la spec sur
  le fond.
- **Point fort à conserver** : le **hors ligne** (`REQUEST.licreq` → `LICENSE.lic`)
  est adapté aux environnements pharma cloisonnés — à prioriser sur le « en ligne ».
- **Point à corriger** : le « super admin caché » → utiliser le **rôle système
  `SUPPORT`** comme mode maintenance (cohérent avec l'autre module, traçable).
- **Intégration** : côté UbiSenderPro, **rester minimal** (client de licence +
  menu Licence + intersection RBAC). Le gros du travail (`UbiLicense Manager`) est
  un **projet séparé** détenant la clé privée.
- **Prérequis n°1** : **trancher §1** (revente ou mono-client). Cela conditionne
  s'il faut construire tout le dispositif ou juste un verrou d'échéance. Tant que ce
  n'est pas décidé, je **ne recommande pas** de démarrer L3/L4.
- **Séquencement conseillé** : L0 (décision) → L1 (client hors ligne) → L2
  (application modules) → puis seulement L3/L4 si la revente est confirmée.
