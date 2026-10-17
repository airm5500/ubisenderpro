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
