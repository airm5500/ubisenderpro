# UBIPHARM CRM (Spring Boot)

Nouvelle plateforme WhatsApp d'UBIPHARM CI (modules Recouvrement puis Marketing),
qui remplace UbiSenderPro. L'architecture est decrite dans le document
« Architecture technique - Plateforme WhatsApp UBIPHARM CI (Spring Boot) ».

## Prerequis

- JDK 25
- MariaDB 10.11 ou plus, avec une base `ubicrm` (utf8mb4) et un utilisateur dedie
- Maven : fourni par le wrapper (`mvnw` / `mvnw.cmd`)

```sql
CREATE DATABASE ubicrm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ubicrm'@'localhost' IDENTIFIED BY 'ubicrm';
GRANT ALL ON ubicrm.* TO 'ubicrm'@'localhost';
```

L'acces a la base se regle par variables d'environnement : `CRM_DB_URL`,
`CRM_DB_USER`, `CRM_DB_PASSWORD` (valeurs par defaut : base locale `ubicrm`).

## Lancer

```
mvnw.cmd spring-boot:run        (Windows)
./mvnw spring-boot:run          (Linux)
```

Flyway cree le schema au demarrage. Le compte `admin` recoit au premier
demarrage le mot de passe du parametre `admin.mot_de_passe_initial`
(`Admin@2026`), qui est ensuite efface de la base : changez-le des la premiere
connexion.

## Tests

- `./mvnw test` : tests unitaires.
- `./mvnw verify -Pit` : tests d'integration contre une vraie base MariaDB
  `ubicrm_test` (elle est videe a chaque lancement, ne jamais pointer sur la
  production). Base reglable par `CRM_DB_URL_TEST`.

## Conventions de code

Chaque module (`commun`, `securite`, puis `recouvrement`, `marketing`...) suit
le meme decoupage :

| Package | Contenu |
| --- | --- |
| `ci.ubipharm.crm.<module>.rest` | Ressources : uniquement les routes, aucune logique metier |
| `....rest.service` | Interfaces des services |
| `....rest.service.impl` | Implementations : logique metier, requetes, `mapTupleToDto(Tuple)` |
| `....rest.service.dto` | DTO Lombok `@Builder @Getter @Setter` |

Les lectures passent en GET avec des query params, les ecritures en POST/PUT
(protegees par CSRF). Le cloisonnement par agence s'applique dans les requetes SQL.

## API disponible (socle)

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Jeton CSRF (cookie `XSRF-TOKEN`, en-tete `X-XSRF-TOKEN`) |
| POST | `/api/auth/connexion` | Connexion `{login, motDePasse}` |
| GET | `/api/auth/moi` | Utilisateur connecte, roles, modules, agences |
| POST | `/api/auth/deconnexion` | Deconnexion |
| GET | `/api/agences` | Agences visibles |
| GET | `/api/clients?q=&agenceId=&segmentation=&statut=&page=&taille=` | Recherche paginee |
| GET | `/api/clients/{id}` | Fiche client |

## Imports (lot 2)

Chaque import passe par l'**assistant d'import**, comme dans UbiSenderPro : choix du fichier, apercu des
colonnes avec correspondance proposee (modifiable, enregistrable en modele), simulation, correction des
lignes rejetees, puis validation. La simulation accepte une partie multipart `parametres`
`{correspondance:{champ:colonne}}` ou `{modeleId}` ; sans elle, la correspondance est devinee.

Chaque import se fait en deux temps : une **simulation** qui evalue chaque ligne sans rien modifier
(creation, mise a jour avec ses ecarts, identique, ou rejet avec son motif), puis une **validation**
qui re-evalue les lignes contre la base et applique les creations et les mises a jour choisies.
Formats acceptes : `.xlsx`, `.xls`, `.csv` (separateur `;`, `,` ou tabulation, UTF-8 ou Windows-1252),
10 Mo et 20 000 lignes au plus (parametre `import.lignes_max`). Les en-tetes sont reconnus sans tenir
compte des accents ni de la casse (`Date d'échéance`, `date_echeance`, `Echeance`...).

| Methode | Route | Role |
| --- | --- | --- |
| POST | `/api/imports/clients` (multipart `fichier`) | Simulation d'un import clients |
| POST | `/api/imports/clients/{id}/validation` | Validation `{toutesLesMisesAJour, clesAMettreAJour[]}` |
| POST | `/api/recouvrement/imports/factures` (multipart `fichier`) | Simulation d'un import factures |
| POST | `/api/recouvrement/imports/factures/{id}/validation` | Validation, meme corps |
| GET | `/api/imports?type=&page=&taille=` | Historique des imports |
| GET | `/api/imports/{id}` | Bilan d'un import |
| GET | `/api/imports/{id}/lignes?action=&page=&taille=` | Detail ligne par ligne |
| POST | `/api/imports/{id}/annulation` | Abandon d'une simulation |
| GET | `/api/imports/{type}/champs` | Assistant : champs attendus (`type` = `clients`, `factures`) |
| GET | `/api/imports/{type}/exemplaire` | Assistant : exemplaire CSV a remplir, reimportable tel quel |
| POST | `/api/imports/{type}/apercu?modeleId=` (multipart `fichier`) | Assistant : colonnes lues, 3 exemples par colonne, correspondance proposee, champs obligatoires manquants |
| GET / POST | `/api/imports/{type}/modeles` | Assistant : modeles de correspondance enregistres |
| POST | `/api/imports/modeles/{id}/archivage` | Archive un modele |
| PUT | `/api/imports/{id}/lignes/{ligneId}` | Corrige une ligne de la simulation `{valeurs:{champ:valeur}}`, tout le fichier est re-evalue |
| GET | `/api/imports/{id}/rejets` | Lignes rejetees et leur motif en CSV, a corriger puis reimporter |
| GET | `/api/recouvrement/factures?q=&clientId=&agenceId=&sousTypeId=&statut=&nature=&echeanceDu=&echeanceAu=&enRetard=&page=&taille=` | Recherche paginee |
| GET | `/api/recouvrement/sous-types` | Sous-types de compte |

Colonnes clients : celles de l'extraction UBIPHARM (`Numérodeompte`, `Agce`, `Sgce`, `Seg`, `Nom`, `Adresse1`,
`WhatsappMAJ`, `PharmacienTitulaire`, `PharmacienAss.`, `Chargédecde`, `Comptable`, `E-MailMAJ`, `Commentair`)
ou leurs equivalents (code client, raison sociale, agence...). `Adresse1` est le nom du pharmacien titulaire,
`Seg` un segment de `crm_segment` (P Platinum, S Silver, B Bronze). Une cellule a `0` vaut « non renseigne ».
L'agence est reconnue par son code, son libelle ou sa lettre UBIPHARM (`crm_agence.code_import`). Colonnes factures : reference, code client, date facture, date d'echeance, montant,
solde ; en option sous-type (Normal par defaut) et nature (facture ou avoir ; un montant negatif est un avoir).
Une cellule vide n'efface jamais une valeur existante, un ancien numero WhatsApp est archive et non supprime.
Imports reserves aux profils ADMIN, DIR_RECOUVREMENT et DIR_AGENCE (limite a ses agences).

## Portefeuilles agents (lot 3)

Chaque client a au plus un agent en cours (contrainte en base). Une reaffectation clot l'affectation
precedente avec son motif et garde l'historique ; rien n'est supprime. Un agent ne voit que les clients et
factures de son portefeuille, plus ceux des agents qu'il remplace pendant un interim en cours. La direction
voit ses agences. Affectations reservees a ADMIN, DIR_RECOUVREMENT et DIR_AGENCE (ses agences) ; interims et
numeros WhatsApp des agents reserves a ADMIN et DIR_RECOUVREMENT.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/recouvrement/portefeuille?agentId=&agenceId=&q=&sansAgent=&avecSolde=&page=&taille=` | Clients du portefeuille avec factures ouvertes, solde et retard max |
| POST | `/api/recouvrement/affectations` | Affecte `{agentId, clientIds[], motif}` ; renvoie traites, inchanges et refus motives |
| POST | `/api/recouvrement/affectations/retrait` | Retire `{clientIds[], motif}` de leur agent |
| POST | `/api/recouvrement/agents/{id}/transfert` | Transfere tout le portefeuille `{versAgentId, motif}` |
| GET | `/api/recouvrement/clients/{id}/affectations` | Historique des agents d'un client |
| GET | `/api/recouvrement/agents?agenceId=` | Agents de recouvrement, taille du portefeuille, remplacant en cours |
| PUT | `/api/recouvrement/agents/{id}/numero` | Numero WhatsApp de l'agent `{numeroWhatsapp}`, normalise et unique |
| GET | `/api/recouvrement/delegations?agentId=&enCours=` | Interims |
| POST | `/api/recouvrement/delegations` | Interim `{titulaireId, remplacantId, dateDebut, dateFin, motif}`, sans chevauchement |
| POST | `/api/recouvrement/delegations/{id}/annulation` | Annule un interim |

## Campagnes de relance (lot 4)

Une facture = un message, envoye au numero WhatsApp principal du client depuis le numero de son agent.
Cycle : **brouillon** -> **a valider** (soumission) -> **validee** (la Direction du Recouvrement valide, les
messages sont figes) -> **en cours** a la date d'execution (vide = des la validation) -> terminee. Une campagne
peut etre rejetee avec un motif (elle redevient brouillon) ou annulee (messages non envoyes annules, factures
remises a relancer). Avant validation, le recapitulatif et les messages sont recalcules en direct.

Une facture est exclue, avec son motif, si le client n'a pas d'agent, si l'agent est desactive ou n'a pas de
numero, si le client n'a pas de numero WhatsApp, ou si la facture attend deja l'envoi d'une autre campagne.
Au demarrage, une facture soldee depuis la validation n'est plus relancee. Fenetres par type : avant echeance
J-5 a J-1 (parametres `campagne.avant_echeance_jours_*`), jour de l'echeance, apres echeance (des J+1),
personnalisee (criteres libres). Criteres combinables : agences, agents, clients, sous-types, segments, solde
min/max, periode d'echeance, jours avant echeance, jours de retard. La selection reste dans le perimetre du
createur (un agent : son portefeuille). Le planificateur (`crm.campagnes.planificateur`, chaque minute) demarre
les campagnes programmees ; l'envoi WhatsApp des messages `A_ENVOYER` arrive au lot 5.

Modeles : crees et modifies par ADMIN et DIR_RECOUVREMENT, variables ecrites `{code}` comme dans UbiSenderPro.
Une variable inconnue est refusee. Les variables (`crm_variable_message`) pointent vers une colonne de
`crm_client` ou `crm_facture`, ou une valeur calculee (agence, segment, sous-type, jours de retard, jours avant
echeance, nom et numero de l'agent) : une colonne ajoutee a la base devient une variable sans developpement.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/recouvrement/variables` | Variables actives |
| GET | `/api/recouvrement/variables/sources` | Champs utilisables par une variable |
| POST / PUT | `/api/recouvrement/variables[/{id}]` | `{code, libelle, source, exemple}` |
| POST | `/api/recouvrement/variables/{id}/archivage` | Refuse si un modele actif l'utilise |
| GET | `/api/recouvrement/modeles-messages?typeCampagne=&statut=` | Modeles et variables qu'ils citent |
| POST / PUT | `/api/recouvrement/modeles-messages[/{id}]` | `{nom, typeCampagne, contenu}` |
| POST | `/api/recouvrement/modeles-messages/{id}/archivage` | Archive un modele |
| GET | `/api/recouvrement/campagnes?q=&statut=&typeCampagne=&page=&taille=` | Journal des campagnes |
| POST / PUT | `/api/recouvrement/campagnes[/{id}]` | `{nom, typeCampagne, modeleId, criteres, dateExecution}` |
| GET | `/api/recouvrement/campagnes/{id}/recapitulatif` | Clients, factures, messages, solde, agents, exclusions par motif, 3 exemples |
| GET | `/api/recouvrement/campagnes/{id}/messages?statut=&agentId=&q=&page=&taille=` | Messages generes |
| POST | `/api/recouvrement/campagnes/{id}/soumission` | Auteur ou direction |
| POST | `/api/recouvrement/campagnes/{id}/validation` | ADMIN, DIR_RECOUVREMENT |
| POST | `/api/recouvrement/campagnes/{id}/rejet` | `{motif}`, ADMIN, DIR_RECOUVREMENT |
| POST | `/api/recouvrement/campagnes/{id}/lancement` | Demarre maintenant une campagne programmee |
| POST | `/api/recouvrement/campagnes/{id}/annulation` | `{motif}` ; une fois validee, direction seulement |

## Envoi WhatsApp (lot 5)

Le service compagnon `wa-web/` (Node, Baileys, repris d'UbiSenderPro) tient une session WhatsApp Web par
numero professionnel d'agent. Canal non officiel : debit lent impose pour limiter le risque de blocage.
Voir `wa-web/README.md` pour le lancer ; le CRM le joint par `CRM_WHATSAPP_URL` et partage le jeton
`CRM_WHATSAPP_JETON` (= `WA_WEB_TOKEN`).

- **Liaison** : l'agent (ou ADMIN / DIR_RECOUVREMENT) demarre la session de son numero et scanne le QR.
- **Envoi** : toutes les 5 s (`crm.whatsapp.intervalle`), un message au plus par numero d'agent, puis une attente
  aleatoire de 20 a 45 s ; seulement entre 8 h et 18 h (heure d'Abidjan) et 200 messages par numero et par jour
  (parametres `whatsapp.envoi.*`). Telephone deconnecte : le message attend sans essai compte. Numero absent de
  WhatsApp : echec, la facture redevient a relancer. Service injoignable : 3 essais puis echec. Un message reste
  en cours d'envoi apres un arret brutal passe en echec a verifier, jamais renvoye en double. La campagne se
  termine quand plus rien n'attend.
- **Suivi** : statut du message ENVOYE -> DISTRIBUE -> LU ; statut de la facture RELANCE_ENVOYEE ->
  MESSAGE_DISTRIBUE -> MESSAGE_LU -> REPONSE_RECUE, sans jamais reculer.
- **Reponses** : l'agent repond depuis son WhatsApp ; le CRM garde une copie rattachee au client et a sa
  derniere relance (une reponse vaut lecture).

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/whatsapp/sessions` | Numeros des agents, etat de session, envoyes du jour, messages en attente |
| POST | `/api/whatsapp/sessions/{agentId}/connexion` | Demarre la session, renvoie le QR (data URL) |
| GET | `/api/whatsapp/sessions/{agentId}` | Etat en direct (et QR tant que non lie) |
| POST | `/api/whatsapp/sessions/{agentId}/deconnexion` | Delie le telephone |
| GET | `/api/recouvrement/reponses?clientId=&agentId=&page=&taille=` | Reponses des clients |
| POST | `/api/passerelle/wa-web/status`, `/ack`, `/message` | Callbacks du service, en-tete `X-Api-Token` |

## Interface Angular (lot 6)

L'interface est dans `front/` (Angular 21, PrimeNG 21), d'apres la maquette validee. Construire le front puis
le jar : `cd front && npm install && npm run build`, puis `./mvnw package` copie `front/dist/front/browser` dans
le jar (`/static`) : un seul service sert l'API et l'interface sur le meme port. Une route de l'interface
(`/factures`, `/campagnes/12`...) renvoie `index.html` ; les routes `/api` ne sont jamais concernees. En
developpement, `npm start` sert l'interface sur le port 4200 et relaie `/api` vers le port 8080.

Ecrans du pilote, avec le menu adapte au profil :

| Ecran | Profils | Contenu |
| --- | --- | --- |
| Connexion | tous | Session serveur, jeton CSRF, identifiant memorise au choix |
| Ma journee | agent | A faire (numero deconnecte, campagne renvoyee, factures echues sans relance, reponses), echeances sur 14 jours, ses campagnes |
| Factures | tous | Recherche multicritere, onglets par statut avec compteurs, selection puis « Relancer ces clients » |
| Clients et fiche client | tous | Portefeuille, solde et retard ; fiche avec factures, restant du par sous-type, historique (reponses, agents) |
| Imports | direction | Historique, assistant en 4 etapes (fichier, colonnes et modeles, simulation avec ecarts et corrections, rapport) |
| Portefeuilles agents | pilotage | Agents, numero WhatsApp, affectation et retrait en lot, transfert, interims |
| Campagnes | tous | Onglets par statut, panneau de validation (valider, renvoyer avec motif), creation avec recapitulatif et apercu du message, suivi des envois |
| Modeles de messages | tous (edition : ADMIN, DIR_RECOUVREMENT) | Modeles par type, insertion des variables, apercu avec exemples ; gestion des variables |
| Numeros WhatsApp | agent (le sien), ADMIN et DIR_RECOUVREMENT | Etat des sessions, QR a scanner, envoyes du jour, messages en attente |
| Reponses des clients | tous | Copie des messages recus, rattaches au client et a la campagne |
| Utilisateurs et habilitations | ADMIN | Comptes, un role par module (ou tous les modules), agences, mot de passe provisoire, desactivation |
| Mon mot de passe | tous | Remplacement du mot de passe ; obligatoire a la premiere connexion |

## Comptes et habilitations (lot 7)

Reserve a l'administrateur. Un compte n'est jamais supprime : il est desactive, ses habilitations retirees passent
a inactif et ses rattachements d'agence sont clotures a la veille, pour garder l'historique.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/utilisateurs?q=&statut=&roleCode=&agenceId=&page=&taille=` | Recherche paginee (taille 100 au plus), avec roles et agences en cours |
| GET | `/api/utilisateurs/referentiel` | Roles et modules proposes |
| GET | `/api/utilisateurs/{id}` | Un compte |
| POST | `/api/utilisateurs` | Creation `{login, nomComplet, email, numeroWhatsapp, habilitations:[{roleCode, moduleCode}], agenceIds}` ; renvoie le mot de passe provisoire, une seule fois |
| PUT | `/api/utilisateurs/{id}` | Modification (le login ne change pas) |
| POST | `/api/utilisateurs/{id}/desactivation` et `/reactivation` | Desactiver, reactiver (remet les echecs a zero) |
| POST | `/api/utilisateurs/{id}/reinitialisation-mot-de-passe` | Nouveau mot de passe provisoire ; debloque un compte verrouille |
| POST | `/api/auth/mot-de-passe` | `{actuel, nouveau}` pour l'utilisateur connecte |

Regles :
- `moduleCode` vide = le role vaut pour tous les modules ; il couvre alors les lignes du meme role sur un module.
- Il reste toujours un administrateur actif ; un administrateur ne peut ni se desactiver ni retirer son propre role.
- Mot de passe : `securite.mot_de_passe_longueur_min` caracteres (10 par defaut), lettres et chiffres, sans l'identifiant.
- Mot de passe provisoire (compte cree ou reinitialise, admin initial) : seules les routes `/api/auth` repondent
  tant qu'il n'est pas remplace (403 sinon).
- Desactivation, changement de roles ou d'agences, reinitialisation : la session ouverte de la personne est fermee
  a son appel suivant (401), elle se reconnecte avec ses nouveaux droits (`ControleSessionFiltre`).

## Canal e-mail (lot 8)

Chaque modele de message porte un canal, WhatsApp ou e-mail ; une campagne prend le canal de son modele.
Un modele e-mail a un objet, qui accepte les memes variables que le message. Le canal d'un modele deja utilise
par une campagne ne change plus.

- **Destinataire** : l'adresse e-mail de la fiche client (la premiere si plusieurs sont separees par `;` ou `,`).
  Client sans adresse ou adresse invalide : facture exclue de la campagne, avec le motif.
- **Expediteur** : `email.expediteur` (a defaut `spring.mail.username`), nom affiche `email.nom_expediteur`.
  L'adresse de reponse est l'e-mail de l'agent du client : le client repond directement a son agent.
- **Envoi** : un e-mail a la fois, `email.envoi.delai_secondes` (2) entre deux, `email.envoi.plafond_jour` (1000)
  par jour, entre `email.envoi.heure_debut` et `email.envoi.heure_fin` (8 h a 18 h, heure d'Abidjan).
  Adresse refusee par le serveur : echec immediat, la facture redevient a relancer. Serveur injoignable ou en
  erreur : nouvel essai, puis echec apres `whatsapp.envoi.tentatives_max` essais. Suivi : statut ENVOYE (pas
  d'accuse de lecture par e-mail).
- **Serveur SMTP** : a renseigner dans `application-prod.yml` a cote de l'executable. Sans `spring.mail.host`,
  le canal est signale non configure dans l'ecran de campagne et rien ne part.

```yaml
spring:
  mail:
    host: smtp.exemple.ci
    port: 587
    username: relances@ubipharm.ci
    password: "..."
    properties.mail.smtp.auth: true
    properties.mail.smtp.starttls.enable: true
```

Essai en local : une boite GreenMail (`greenmail-standalone`, SMTP 3025, IMAP 3143) et
`--spring.mail.host=127.0.0.1 --spring.mail.port=3025` ; les tests d'integration (`EmailIT`) l'embarquent.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/email/etat` | Canal configure ou non, expediteur, plage horaire, e-mails en attente et envoyes du jour |
| GET | `/api/recouvrement/modeles-messages?canal=EMAIL` | Modeles filtres par canal |
