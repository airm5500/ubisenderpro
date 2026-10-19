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

Les classes sont regroupees par couche, tous modules confondus (le module se lit dans le nom
de la classe et dans la route, par exemple `CampagneRessource` et `/api/recouvrement/campagnes`) :

| Package | Contenu |
| --- | --- |
| `ci.ubipharm.crm.rest` | Ressources : uniquement les routes, aucune logique metier |
| `ci.ubipharm.crm.rest.service` | Interfaces des services |
| `ci.ubipharm.crm.rest.service.impl` | Implementations : logique metier, requetes, `mapTupleToDto(Tuple)` |
| `ci.ubipharm.crm.rest.service.dto` | DTO Lombok `@Builder @Getter @Setter` |
| `ci.ubipharm.crm.planification` | Taches planifiees (envois WhatsApp, e-mail, SMS, demarrage des campagnes) |
| `ci.ubipharm.crm.socle` | Configuration, securite HTTP, erreurs, DTO communs |

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

## Canal SMS (lot 9)

Troisieme canal, apres WhatsApp et l'e-mail : un modele SMS (612 caracteres au plus) part au numero principal du client,
sous le nom d'expediteur du fournisseur (11 caracteres). Le client ne peut pas repondre : le modele donne le numero de
son agent par `{agent_numero}` (affiche 07 08 12 34 56). Client sans numero : facture exclue, avec le motif.

- **Fournisseurs** (`crm_fournisseur_sms`, ecran Administration > Fournisseurs SMS, ADMIN) : chaque fournisseur est decrit
  par son API HTTP, sans code propre. Adresse, methode, type de contenu (JSON ou formulaire), en-tetes et corps sont des
  gabarits avec `{cle}` `{expediteur}` `{numero}` `{message}` `{reference}` `{url_accuse}` `{identifiant}`, echappes selon
  l'endroit ; un champ JSON reste vide est retire. Les reponses se lisent par chemins JSON (`data.id`, plusieurs separes par
  des virgules). Format des numeros : 225..., +225... ou 10 chiffres. Un seul fournisseur par defaut, celui des relances ;
  il reste actif. La cle API n'est jamais renvoyee par l'API. LeTexto est fourni parametre (cle a saisir).
- **Envoi** : un SMS a la fois, `sms.envoi.delai_secondes` (1), `sms.envoi.plafond_jour` (2000), entre `sms.envoi.heure_debut`
  et `sms.envoi.heure_fin`. Numero refuse (HTTP 4xx) : echec immediat avec le message du fournisseur. Fournisseur injoignable,
  HTTP 429 ou 5xx : nouvel essai. Cle refusee (401/403) : le SMS reprend sa place sans essai compte, les envois s'arretent
  `sms.envoi.pause_cle_refusee_minutes` (10) et l'ecran de campagne affiche l'alerte.
- **Accuses** : avec `application.url_publique` renseigne, le CRM donne au fournisseur l'adresse
  `/api/passerelle/sms/{code}/accuse?jeton=...` (POST JSON ou GET avec parametres). Sinon, ou si l'accuse n'arrive pas, le
  statut des SMS envoyes depuis plus de 5 minutes est demande au fournisseur (`crm.sms.verification`, 2 min). La
  correspondance `SENT=ENVOYE; DELIVERED=DISTRIBUE; FAILED=ECHEC` traduit ses statuts ; un SMS non remis rend la facture
  a relancer.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/sms/etat` | Canal pret ou non, fournisseur, expediteur, plage horaire, en attente, envoyes du jour, alerte |
| GET, POST | `/api/sms/fournisseurs` | Liste, creation (ADMIN) |
| GET, PUT | `/api/sms/fournisseurs/{id}` | Consultation, modification (cle vide = cle gardee) |
| POST | `/api/sms/fournisseurs/{id}/defaut` | Devient le fournisseur des relances |
| POST | `/api/sms/fournisseurs/{id}/essai` | `{numero, message}` : vrai SMS d'essai, renvoie la reponse lue et brute |
| GET | `/api/sms/fournisseurs/{id}/solde` | Solde du compte chez le fournisseur |
| POST, GET | `/api/passerelle/sms/{code}/accuse?jeton=` | Accuses du fournisseur |

## Parametres (lot 10)

Ecran Administration > Parametres (ADMIN) : les parametres de `crm_parametre` par categorie (general, campagnes,
WhatsApp, e-mail, SMS, securite, imports). Chaque parametre porte son type (`TEXTE`, `ENTIER`, `HEURE`, `EMAIL`, `URL`,
`COULEUR`), ses bornes et s'il est obligatoire ; le serveur controle chaque valeur et la coherence des couples (heure de fin
apres l'heure de debut, attente minimale sous l'attente maximale, fenetre avant echeance). Un enregistrement passe en entier
ou pas du tout, chaque changement est journalise (`PARAMETRE_MODIFICATION`, ancienne et nouvelle valeur). Les parametres
internes (`modifiable = false` : mot de passe initial, nom et couleurs pas encore appliques a l'interface) n'apparaissent pas.
Le serveur SMTP reste dans `application-prod.yml`.

| Methode | Route | Role |
| --- | --- | --- |
| GET | `/api/parametres` | Parametres modifiables, par categorie et ordre d'affichage |
| PUT | `/api/parametres` | `[{cle, valeur}]` : enregistre tout ou rien ; renvoie la liste a jour |

## Installation Windows (lot 11)

Le dossier `distribution/` produit le paquet d'installation du poste d'UBIPHARM : un zip qui embarque un Java 25
reduit par jlink (73 Mo), Node.js portable pour le compagnon WhatsApp et WinSW, qui fait du CRM et du compagnon
deux services Windows a demarrage automatique. Seule MariaDB s'installe a part.

- `construire-paquet-windows.ps1` : a lancer sur un poste Windows relie a Internet (avec Git) ; telecharge le JDK
  Temurin 25, Node.js et WinSW, construit l'interface et le jar, assemble et zippe le paquet (environ 160 Mo).
- `modele/` : definitions des deux services (`crm-service.xml`, `wa-web-service.xml`), `installer.bat`,
  `desinstaller.bat` et les modeles de configuration `application-prod.yml` et `wa-web.env`.
- `INSTALLATION.md` : guide d'installation, d'exploitation, de sauvegarde et de mise a jour, joint au paquet.

Sur le poste, `C:\UBIPHARM-CRM\application` est remplace a chaque mise a jour ; `C:\UBIPHARM-CRM\donnees` garde
la configuration, les journaux et la session WhatsApp. `installer.bat` y cree les deux fichiers de configuration a
la premiere execution, avec un jeton commun au CRM et au compagnon, puis installe et demarre les services.
Le compagnon lit son fichier par la variable `WA_WEB_ENV`.

## Roles et droits (lot 12)

Chaque role porte la liste des ecrans et actions qu'il ouvre (`crm_droit`, `crm_role_droit`), cochee par
l'administrateur dans l'ecran **Roles et droits** (`/api/roles`, droit `ROLES`). On y cree aussi ses propres roles,
en partant au besoin d'un role existant ; un role n'est jamais supprime, il se desactive quand plus aucun compte
actif ne l'a. Une case decochee reste en base (`actif = false`) et chaque changement est journalise.

- **Au serveur** : la connexion donne une autorite `DROIT_<code>` par droit coche ; les routes sont protegees par
  `hasAuthority('DROIT_...')` (imports, affectations, interims, validation des campagnes, ecriture des modeles,
  numeros des agents, administration). Les consultations restent limitees au perimetre du compte (ses agences,
  ou son seul portefeuille sans `PERIMETRE_AGENCES`).
- **Par module** : un droit du Recouvrement ne vient que d'un role attribue sur ce module (ou sur tous les modules) ;
  les droits d'administration et `IMPORTS` (clients) valent quel que soit le module.
- **Interface** : menus, boutons et gardes de route suivent les memes droits (`auth.peut(...)`).
- **Garde-fous** : le role Administrateur garde toujours `UTILISATEURS` et `ROLES` et ne se desactive pas. Quand les
  droits d'un role changent, les comptes qui l'ont se reconnectent (`droits_revises_le`).
- Les roles livres gardent les droits qu'ils avaient avant ce lot (migration `V1.0.17`).

## Creation des clients a la main (lot 13)

Les clients arrives un par un se saisissent sans import : bouton **Nouveau client** dans Clients, **Modifier** sur la fiche.

- **Droit** `CLIENTS_GERER` (Creances, tous modules), donne par la migration `V1.0.18` a chaque role qui importe deja
  des clients (Administrateur, directions). Les agents ne l'ont pas ; il se coche dans Roles et droits.
- **API** : `POST /api/clients`, `PUT /api/clients/{id}`, `GET /api/clients/segments`. Memes controles que l'import :
  numero de compte unique (non modifiable ensuite), agence du perimetre, segment connu, e-mail valide, numeros a
  10 chiffres normalises avec le prefixe `whatsapp.prefixe_pays`.
- **Numeros** : les six types de l'import ; le principal sert aux relances. Un numero remplace ou efface est archive
  (`statut = 'ARCHIVE'`), jamais supprime.
- **Agent** : a la creation, un profil qui affecte les portefeuilles peut choisir l'agent tout de suite.
- **Journal** : `CLIENT_CREATION`, et `CLIENT_MODIFICATION` avec chaque champ change (avant -> apres).

## Creation des factures a la main (lot 14)

Regle : tout ce qui s'importe peut aussi se saisir. **Nouvelle facture** dans Factures (et sur la fiche client),
**Modifier** sur chaque ligne pour corriger une facture ou enregistrer un reglement.

- **Droit** `FACTURES_GERER` (Creances, Recouvrement), donne par `V1.0.19` aux roles qui importent deja.
- **API** : `GET /api/recouvrement/factures/{id}`, `POST /api/recouvrement/factures`, `PUT /api/recouvrement/factures/{id}`.
- **Controles de l'import** : reference unique (figee ensuite, comme le client), client du perimetre (et du portefeuille
  pour un agent), sous-type connu, echeance apres la date de facture, solde au plus egal au montant. Un avoir est
  enregistre en negatif. Un solde a 0 solde la facture ; une facture soldee dont le solde reapparait repasse a relancer.
- **Journal** : `FACTURE_CREATION`, `FACTURE_MODIFICATION` (avant -> apres).

## Marketing : listes de diffusion (Marketing lot 1)

Ecran **Marketing > Listes de diffusion**. Une liste vise des clients par criteres (agences, sous-agences, villes,
segments ; un critere vide ne filtre pas), completee ou corrigee client par client (ajout, exclusion). Le nombre de
clients vises se recalcule pendant la saisie. Une liste n'est jamais supprimee : elle est archivee puis reactivable.
Le critere "tournee" n'existe pas : les tournees sont decidees par les agences.

- **Perimetre** : un compte ne voit et n'ajoute que les clients de ses agences (sauf vision globale). Clients archives exclus.
- **Joignable** : client non desabonne qui a au moins un numero WhatsApp des fonctions choisies (principal par defaut) ou un e-mail.
- **Desabonnement STOP** : un message WhatsApp recu dont le texte, sans accents ni ponctuation, est l'un des mots du
  parametre `marketing.mots_desabonnement` (STOP, ARRET, DESABONNER par defaut) desabonne le client de tout le Marketing.
  Les relances du Recouvrement continuent. Desabonner et reabonner a la main depuis la liste.
- **Droits** `LISTES` (consulter) et `LISTES_GERER` (creer, modifier, archiver, desabonner), groupe Diffusion du module
  Marketing ; nouveau role systeme `CHARGE_MARKETING` (Charge marketing). Migration `V1.0.20`.
- **API** (`/api/marketing`) : `GET /listes`, `GET /listes/{id}`, `GET /listes/{id}/membres?filtre=TOUS|JOIGNABLES|NON_JOIGNABLES|EXCLUS`,
  `GET /listes/criteres`, `POST /listes/simulation`, `POST /listes`, `PUT /listes/{id}`, `POST /listes/{id}/clients`
  (`sens` AJOUT, EXCLUSION ou RETRAIT), `POST /listes/{id}/archivage`, `POST /listes/{id}/reactivation`,
  `POST /clients/{id}/desabonnement`, `POST /clients/{id}/reabonnement`.
- **Journal** : `LISTE_CREATION`, `LISTE_MODIFICATION`, `LISTE_CLIENTS`, `LISTE_ARCHIVAGE`, `LISTE_REACTIVATION`,
  `CLIENT_DESABONNEMENT`, `CLIENT_REABONNEMENT`.

## Marketing : campagnes promotionnelles (Marketing lot 2)

Ecrans **Marketing > Campagnes promotionnelles**, **Modeles promotionnels** et **Mon numero WhatsApp**. Une campagne =
une liste de diffusion + un modele du Marketing (WhatsApp, e-mail ou SMS) + une date d'envoi (vide = des la validation).
Meme cycle que les relances : brouillon, soumission, validation ou renvoi avec motif, envoi, annulation.

- **Expediteur** : le WhatsApp part du numero de la personne qui prepare la campagne (numero saisi par l'administrateur
  dans Utilisateurs, relie par QR dans Mon numero WhatsApp) ; l'e-mail part de l'adresse d'UBIPHARM avec son adresse en
  reponse ; le SMS part au nom du fournisseur. Sans numero WhatsApp, la campagne ne peut pas etre soumise.
- **Destinataires** : les clients de la liste dans le perimetre fige de l'auteur ; un message par numero des fonctions
  de la liste (un numero partage par deux clients ne recoit qu'un message), ou un e-mail par client. Exclus avec motif :
  desabonne (STOP), sans numero, sans adresse. Un client desabonne entre la validation et l'envoi ne recoit rien.
- **Validation** : droit `CAMPAGNES_MARKETING_VALIDER` (directeur d'agence, directions, administrateur). Un valideur
  limite a ses agences ne valide pas une campagne qui vise d'autres agences (`crm_campagne.agences_cibles`).
- **Modeles** : `crm_modele_message.module = MARKETING`, type `PROMOTION` ; seules les variables du client et de
  l'expediteur (`{expediteur_nom}`, `{expediteur_numero}`, `{segment}`, `{agence}`) sont admises, pas celles des factures.
- **Tables** : memes `crm_campagne` (colonnes `module`, `liste_id`, `agences_cibles`) et `crm_campagne_message`
  (`facture_id` vide) que les relances ; la chaine d'envoi WhatsApp, e-mail et SMS est commune. Migration `V1.0.21`.
- **Droits** (groupe Campagnes, module Marketing) : `CAMPAGNES_MARKETING`, `CAMPAGNES_MARKETING_PREPARER`,
  `CAMPAGNES_MARKETING_VALIDER`, `MODELES_MARKETING`, `NUMERO_MARKETING`. Le Charge marketing a tout sauf la validation.
- **API** : `/api/marketing/campagnes` (`GET`, `POST`, `PUT /{id}`, `GET /{id}/recapitulatif`, `GET /{id}/messages`,
  `POST /{id}/soumission|validation|rejet|lancement|annulation`), `/api/marketing/modeles`, `GET /api/marketing/variables`.
- **Journal** : `CAMPAGNE_MARKETING_CREATION`, `_MODIFICATION`, `_SOUMISSION`, `_VALIDATION`, `_REJET`, `_LANCEMENT`, `_ANNULATION`.

## Marketing : catalogue des produits (Marketing lot 3)

Ecran **Marketing > Catalogue des produits**. L'equipe Marketing importe son fichier Excel de produits ou les saisit a
la main ; un produit n'est jamais supprime, il est archive puis reactive au besoin.

- **Fiche produit** : code (unique, archives compris), libelle, categorie, laboratoire, conditionnement, prix public
  indicatif en FCFA (facultatif), description. Table `crm_produit`, migration `V1.0.22`.
- **Saisie a la main** : bouton Nouveau produit ; categories et laboratoires deja saisis sont proposes a la frappe.
  Un produit archive ne se modifie pas : on le reactive d'abord.
- **Import** : bouton Importer des produits, meme assistant que les clients et les factures (correspondance des
  colonnes, modeles de correspondance, simulation, correction des lignes, rapport des rejets). Colonnes reconnues
  par exemple : Code article, Designation, Famille, Labo, PP. Une cellule vide n'efface jamais une valeur ; un produit
  deja connu n'est mis a jour que si on le coche ; un produit archive n'est pas reactive par le fichier.
- **Droits** (groupe Catalogue, module Marketing) : `PRODUITS` (consulter) et `PRODUITS_GERER` (importer, saisir,
  modifier, archiver), donnes aux roles Administrateur, DG, Directeur commercial, Directeur d'agence et Charge
  marketing. L'import des produits ne demande pas le droit `IMPORTS` : sans lui, l'historique des imports ne montre
  que les imports du catalogue.
- **API** : `/api/marketing/produits` (`GET` avec `q`, `categorie`, `laboratoire`, `statut`, `GET /filtres`, `POST`,
  `PUT /{id}`, `POST /{id}/archivage|reactivation`), `/api/marketing/imports/produits` (simulation et validation) et
  `/api/imports/produits/...` (etapes communes de l'assistant).
- **Journal** : `PRODUIT_CREATION`, `PRODUIT_MODIFICATION`, `PRODUIT_ARCHIVAGE`, `PRODUIT_REACTIVATION`.

## Marketing : promotions (Marketing lot 4)

Ecran **Marketing > Promotions**. Une promotion = un code, un nom, une periode (debut, fin) et des produits du
catalogue avec leur avantage. La personne qui la cree en est la responsable.

- **Avantages** : unites gratuites en nombre (« 2 offerts pour 10 achetes »), unites gratuites en pourcentage avec
  plafond facultatif (« 10 % d'unites gratuites des 20 achetes, 5 au plus »), ou prix promotionnel (« 2 200 F au lieu
  de 2 500 F », toujours inferieur au prix public du catalogue). Une quantite minimale s'applique a chacun.
- **Cycle** : brouillon, puis active (au moins un produit) ; le lendemain de sa date de fin, elle passe d'office en
  terminee (planificateur chaque minute) et n'est plus proposee aux campagnes ; prolongee, elle redevient active.
  Archivage et reactivation (retour en brouillon) a la main. Un produit se retire de la promotion, il n'est pas
  supprime ; une promotion active garde au moins un produit.
- **Produits a la main ou par import** : bouton Ajouter un produit (recherche dans le catalogue), ou assistant d'import
  (type `PROMOTIONS`) : une ligne = code promotion, code produit, quantite minimale, unites offertes, % d'unites
  gratuites, plafond, prix promotionnel ; un meme fichier peut remplir plusieurs promotions. Exporter donne la liste
  des produits d'une promotion au meme format, reimportable.
- **Campagnes** : une campagne Marketing coche une ou plusieurs promotions actives. Les variables `{promotion_nom}`,
  `{promotion_code}`, `{promotion_debut}`, `{promotion_fin}` et `{produits_promotion}` (un produit par ligne avec son
  avantage) se remplissent d'office ; un modele qui les cite exige une promotion, et une promotion terminee entre-temps
  bloque la soumission et la validation. Ces variables sont refusees dans les modeles de relance.
- **Tables** : `crm_promotion`, `crm_promotion_produit` (ligne retiree = `actif` faux), `crm_campagne.promotion_ids`.
  Migration `V1.0.23`.
- **Droits** (groupe Catalogue, module Marketing) : `PROMOTIONS`, `PROMOTIONS_GERER`, memes roles que le catalogue.
- **API** : `/api/marketing/promotions` (`GET` avec `q` et `statut`, `POST`, `PUT /{id}`,
  `POST /{id}/activation|archivage|reactivation`, `GET /{id}/produits`, `GET /{id}/produits/export`,
  `POST /{id}/produits`, `PUT /{id}/produits/{ligne}`, `POST /{id}/produits/{ligne}/retrait`),
  `/api/marketing/imports/promotions` (simulation et validation).
- **Journal** : `PROMOTION_CREATION`, `_MODIFICATION`, `_ACTIVATION`, `_ARCHIVAGE`, `_REACTIVATION`, `_FIN` (systeme),
  `PROMOTION_PRODUIT_AJOUT`, `_MODIFICATION`, `_RETRAIT`.

## Campagnes automatiques et recurrentes (pilote)

Champ **Repetition** des campagnes de relance et des campagnes Marketing : une seule fois (par defaut), chaque jour,
chaque semaine ou chaque mois, jusqu'a une date de fin obligatoire. La campagne est validee une fois, puis repart
seule a la meme heure (les envois se calculent depuis le premier ; un envoi manque, serveur arrete, n'est pas
rattrape).

- **Relances** : a chaque envoi apres le premier, les messages sont regeneres avec les factures du jour. Une facture
  deja relancee par la campagne avant ou le jour de l'echeance ne l'est plus ; apres l'echeance, elle l'est a chaque
  envoi tant qu'elle n'est pas soldee (et jamais si le message de l'envoi precedent n'est pas encore parti).
- **Marketing** : a chaque envoi, la liste de diffusion et les promotions du moment. La campagne s'arrete d'elle-meme
  (statut annulee, motif explique) si une promotion citee est terminee, si le modele est archive ou si son auteur ne
  peut plus envoyer (compte desactive, plus de numero WhatsApp).
- **Statuts** : entre deux envois, la campagne est « Programmee » avec la date du prochain envoi ; apres le dernier,
  « Executee ». L'annulation arrete la repetition. Les compteurs cumulent tous les envois.
- **Tables** : `crm_campagne.recurrence`, `fin_recurrence`, `debut_recurrence`, `nb_executions` ;
  `crm_campagne_message.execution` (numero de l'envoi). Migration `V1.0.24`.

## Marketing : pieces jointes (pilote)

- **Affiche** : image JPEG ou PNG, ou PDF (type verifie sur le contenu du fichier, pas sur son nom), 5 000 Ko au plus
  (parametre `marketing.affiche_taille_max_ko`). Deposee depuis le formulaire de campagne.
- **Liste des produits** : case « Joindre la liste Excel des produits des promotions cochees ». Le classeur
  (`produits_en_promotion.xlsx` : promotion, fin, code, produit, conditionnement, prix public, quantite minimale,
  avantage, prix promotionnel) est etabli a la validation, puis a chaque envoi d'une campagne recurrente.
- **Envoi WhatsApp** : une affiche en image porte le texte du message en legende (texte de 1 000 caracteres au plus ;
  au-dela, l'affiche puis le texte) ; une affiche PDF part en document avant le texte ; la liste Excel suit en
  document. Le message compte comme envoye des que son texte est parti ; une piece jointe perdue ensuite est notee
  dans son motif, sans renvoyer le texte.
- **E-mail** : affiche et liste en pieces jointes du courrier. **SMS** : refuse les pieces jointes.
- **Tables** : `crm_piece_jointe` (contenu en base, jamais supprime), `crm_campagne.affiche_id`, `joindre_produits`,
  `liste_produits_id`. Migration `V1.0.25`.
- **API** : `POST /api/marketing/pieces-jointes` (multipart, champ `fichier`), `GET /api/marketing/pieces-jointes/{id}`
  (droit `CAMPAGNES_MARKETING`) ; `afficheId` et `joindreProduits` dans la demande de campagne.

## Promesse de paiement, litige et contentieux (pilote)

Depuis la liste des factures, lien **Suivi** (droit `FACTURES_SUIVI` : agent, directeur d'agence, direction du recouvrement, admin) :

- **Promesse de paiement** : date promise (aujourd'hui ou plus tard), montant promis facultatif (positif, au plus le solde), commentaire facultatif.
  La facture n'est plus relancee jusqu'a cette date incluse ; le lendemain, si elle n'est pas soldee, elle redevient relancable
  (filtre « Promesse non tenue »).
- **Litige** : motif obligatoire ; relances suspendues jusqu'a la levee.
- **Contentieux** : motif obligatoire ; seul le droit `FACTURES_CONTENTIEUX` (direction du recouvrement, admin) le pose ou le leve.
- **Lever et reprendre les relances** : efface le suivi en cours.

Une facture soldee ou un avoir ne prend pas de suivi. Chaque changement est garde dans `crm_facture_suivi` (historique affiche
dans la fenetre) et journalise (`FACTURE_SUIVI`). Le recapitulatif d'une campagne compte ces factures dans les exclusions avec
le motif « Promesse de paiement jusqu'au jj/mm/aaaa », « Facture en litige » ou « Facture au contentieux », quel que soit le canal.

API : `POST /api/recouvrement/factures/{id}/suivi` (`{"suivi":"PROMESSE","datePromesse":"2026-10-31","montantPromis":50000,"commentaire":"..."}`,
corps sans `suivi` pour lever), `GET /api/recouvrement/factures/{id}/suivis`, filtre `suivi=PROMESSE|PROMESSE_ECHUE|LITIGE|CONTENTIEUX|AUCUN`.

## Delais de relance par agence et par segment (pilote)

Menu **Relances > Delais de relance** (droit `DELAIS_RELANCE` : admin, direction du recouvrement, directeur d'agence).

- **Par defaut** : les parametres `campagne.avant_echeance_jours_max` / `_min` (J-5 a J-1), `campagne.apres_echeance_jours_min` (J+1)
  et `campagne.relance_intervalle_jours` (0 = pas de minimum entre deux relances d'une meme facture), modifiables dans Parametres.
- **Regles** (`crm_delai_relance`) pour une agence, un segment (Platinum, Silver, Bronze) ou les deux. Une valeur vide reprend
  le niveau au-dessus : agence + segment, puis agence, puis segment, puis defaut. Une seule regle active par couple ; on archive,
  on ne supprime pas. Un directeur d'agence ne regle que ses agences ; les regles communes a toutes les agences sont reservees
  a la vision globale.
- Les campagnes **Avant echeance** et **Apres echeance** sans fenetre propre appliquent a chaque facture les delais de l'agence
  et du segment de son client. Une fenetre saisie dans la campagne reste prioritaire.
- **Intervalle** : une facture relancee (message envoye, distribue ou lu, toutes campagnes) moins de n jours avant est exclue avec
  le motif « Deja relancee le jj/mm/aaaa (n jours minimum entre deux relances) ».

API : `GET /api/recouvrement/delais-relance[?archives=true]`, `GET .../defauts`, `POST`, `PUT /{id}`, `POST /{id}/archivage`,
`POST /{id}/reactivation`.

## Tableaux de bord (pilote)

- **Recouvrement** : menu **Mon activite > Tableau de bord** (droit `TABLEAU_BORD`, donne a tous les roles qui voient les
  campagnes). L'agent voit son portefeuille (et celui qu'il remplace), le directeur d'agence ses agences, la direction tout ;
  filtres periode (30 derniers jours par defaut, un an au plus), agence et agent.
  - Creances du jour : solde du, echu, echeances proches (`tableau_bord.echeance_proche_jours`, 7), promesses en cours et non
    tenues, litiges, contentieux ; balance agee ; repartition par sous-type de compte.
  - Relances de la periode : tentees, envoyees, distribuees, lues, echecs ; taux de reussite, d'echec, de reponse (clients
    relances qui ont repondu) et de conversion (factures relancees dont le solde a baisse apres la premiere relance).
  - Resultats : montant encaisse et factures soldees (historique `crm_facture_solde`, alimente par la saisie et l'import),
    promesses enregistrees, campagnes par statut ; performances par agence et par agent ; anomalies (motifs d'echec,
    campagnes arretees automatiquement).
- **Marketing** : menu **Marketing > Tableau de bord** (droit `TABLEAU_BORD_MARKETING`) : audience joignable, listes, promotions
  en cours, desabonnements, messages par canal, taux de lecture et de reponse, campagnes lancees sur la periode.

API : `GET /api/recouvrement/tableau-de-bord?du=&au=&agenceId=&agentId=`, `GET /api/marketing/tableau-de-bord?du=&au=`.

## Messages de disponibilite (pilote)

- **Catalogue** : colonne **Stock** et filtre **Disponibilite** (disponibles, en rupture, de retour en stock). Actions
  **Rupture** et **De retour** sur chaque produit (droit `PRODUITS`), date et auteur conserves ; l'import produits accepte une
  colonne facultative **Disponibilite** (Disponible / Rupture).
- Un produit est **de retour en stock** s'il est redevenu disponible depuis moins de `marketing.retour_stock_jours` jours (15).
- **Annoncer un retour** : cocher des produits dans le catalogue puis **Annoncer leur retour aux clients**, ou les cocher dans
  le formulaire de campagne promotionnelle (section **Produits de retour en stock annonces**). La variable
  `{produits_disponibles}` (modeles marketing uniquement) liste les produits coches ; la liste Excel jointe peut les inclure.
- Une campagne qui annonce un produit reparti en rupture est bloquee a la demande, a la soumission, a la validation et a
  chaque envoi recurrent.

API : `POST /api/marketing/produits/{id}/rupture`, `POST /api/marketing/produits/{id}/retour-stock`,
`GET /api/marketing/produits?disponibilite=RUPTURE|DISPONIBLE|RETOUR`.
