-- =====================================================================
-- UBIPHARM CRM - Schema initial (socle)
-- Parametres, modules, roles, utilisateurs et habilitations, agences,
-- clients et contacts, journaux.
-- Rien n'est supprime definitivement : les lignes portent un statut
-- (actif / archive) a la place.
-- =====================================================================

CREATE TABLE crm_parametre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cle VARCHAR(100) NOT NULL,
    valeur VARCHAR(2000),
    description VARCHAR(500),
    categorie VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_parametre_cle (cle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_module (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_module_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    -- vision globale : toutes les agences (Direction du Recouvrement, DG, DC, admin)
    vision_globale BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_utilisateur (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login VARCHAR(100) NOT NULL,
    nom_complet VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    numero_whatsapp VARCHAR(30),
    mot_de_passe_hash VARCHAR(255) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    echecs_connexion INT NOT NULL DEFAULT 0,
    derniere_connexion DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_utilisateur_login (login),
    CONSTRAINT ck_crm_utilisateur_statut CHECK (statut IN ('ACTIF', 'DESACTIVE', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Habilitation = un role sur un module. module_id NULL = tous les modules.
CREATE TABLE crm_habilitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    module_id BIGINT,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_habilitation (utilisateur_id, role_id, module_id),
    CONSTRAINT fk_crm_hab_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_hab_role FOREIGN KEY (role_id) REFERENCES crm_role(id),
    CONSTRAINT fk_crm_hab_module FOREIGN KEY (module_id) REFERENCES crm_module(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_agence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_agence_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rattachement utilisateur <-> agence, multiple et historise (date_fin NULL = en cours)
CREATE TABLE crm_utilisateur_agence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT NOT NULL,
    agence_id BIGINT NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_ua_utilisateur (utilisateur_id, date_fin),
    CONSTRAINT fk_crm_ua_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_ua_agence FOREIGN KEY (agence_id) REFERENCES crm_agence(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    raison_sociale VARCHAR(255) NOT NULL,
    segmentation VARCHAR(50),
    agence_id BIGINT NOT NULL,
    ville VARCHAR(100),
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_client_code (code),
    KEY idx_crm_client_agence (agence_id, statut),
    KEY idx_crm_client_raison (raison_sociale),
    CONSTRAINT fk_crm_client_agence FOREIGN KEY (agence_id) REFERENCES crm_agence(id),
    CONSTRAINT ck_crm_client_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deux numeros par officine : rang 1 = titulaire, rang 2 = employe
CREATE TABLE crm_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    nom VARCHAR(255),
    fonction VARCHAR(100),
    numero_whatsapp VARCHAR(30) NOT NULL,
    rang INT NOT NULL DEFAULT 1,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_crm_contact_client (client_id, rang),
    CONSTRAINT fk_crm_contact_client FOREIGN KEY (client_id) REFERENCES crm_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_journal_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT,
    login VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    entite VARCHAR(100),
    entite_id BIGINT,
    details TEXT,
    adresse_ip VARCHAR(60),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_journal_utilisateur (utilisateur_id),
    KEY idx_crm_journal_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_journal_connexion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT,
    login VARCHAR(100) NOT NULL,
    succes BOOLEAN NOT NULL,
    adresse_ip VARCHAR(60),
    navigateur VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_jc_login (login, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
