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
