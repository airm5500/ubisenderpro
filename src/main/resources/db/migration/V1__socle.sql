-- =====================================================================
-- UbiSenderPro - Phase 1 : Socle technique
-- Tables : paramètres, utilisateurs, rôles, permissions, journal
-- =====================================================================

CREATE TABLE usp_parametre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cle VARCHAR(100) NOT NULL,
    valeur VARCHAR(2000),
    description VARCHAR(500),
    categorie VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_parametre_cle (cle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    module VARCHAR(80),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_utilisateur (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login VARCHAR(100) NOT NULL,
    nom_complet VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    mot_de_passe_hash VARCHAR(255) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_connexion DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_utilisateur_login (login),
    KEY idx_usp_utilisateur_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_utilisateur_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_utilisateur_role (utilisateur_id, role_id),
    CONSTRAINT fk_usp_ur_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES usp_utilisateur(id),
    CONSTRAINT fk_usp_ur_role FOREIGN KEY (role_id) REFERENCES usp_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_role_permission (role_id, permission_id),
    CONSTRAINT fk_usp_rp_role FOREIGN KEY (role_id) REFERENCES usp_role(id),
    CONSTRAINT fk_usp_rp_permission FOREIGN KEY (permission_id) REFERENCES usp_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_journal_action (
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
    KEY idx_usp_journal_utilisateur (utilisateur_id),
    KEY idx_usp_journal_action (action),
    KEY idx_usp_journal_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----- Données de référence : rôles (section 26.2 de la spec) -----
INSERT INTO usp_role (code, libelle, description, actif, created_at) VALUES
 ('ADMIN',        'Administrateur',        'Accès complet',                     TRUE, NOW()),
 ('MARKETING',    'Responsable marketing', 'Clients, segments, campagnes',      TRUE, NOW()),
 ('SUPERVISEUR',  'Superviseur',           'Conversations, affectations',       TRUE, NOW()),
 ('AGENT',        'Agent',                 'Conversations affectées',           TRUE, NOW()),
 ('CATALOGUE',    'Gestionnaire catalogue','Articles, catégories, marques',     TRUE, NOW()),
 ('LECTURE',      'Lecture seule',         'Consultation autorisée',            TRUE, NOW());

-- ----- Compte administrateur par défaut -----
-- Login : admin   /   Mot de passe initial : voir paramètre 'admin.mot_de_passe_initial'.
-- Le hash BCrypt est calculé par l'application au premier démarrage (voir Bootstrap.java),
-- car le hash ne peut pas être généré de façon fiable en SQL pur.
INSERT INTO usp_utilisateur (login, nom_complet, email, mot_de_passe_hash, actif, created_at) VALUES
 ('admin', 'Administrateur', 'admin@ubisenderpro.local', 'INIT', TRUE, NOW());

INSERT INTO usp_utilisateur_role (utilisateur_id, role_id)
SELECT u.id, r.id FROM usp_utilisateur u, usp_role r WHERE u.login = 'admin' AND r.code = 'ADMIN';

-- ----- Paramètres par défaut -----
INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('app.nom',             'UbiSenderPro',  'Nom de l''application',            'GENERAL', NOW()),
 ('whatsapp.prefixe_pays','225',          'Préfixe pays par défaut (CI)',     'WHATSAPP', NOW()),
 ('import.taille_max_mo','10',            'Taille maximale d''un import (Mo)','IMPORT', NOW()),
 ('session.expiration_min','60',          'Expiration de session (minutes)',  'SECURITE', NOW()),
 ('admin.mot_de_passe_initial','Admin@2026','Mot de passe initial du compte admin (haché au 1er démarrage puis ignoré)','SECURITE', NOW());
