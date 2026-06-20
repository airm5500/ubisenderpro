-- =====================================================================
-- UbiSenderPro - Phase 1 : Référentiel clients
-- Tables : segmentation, comptes clients, contacts, tags
-- =====================================================================

CREATE TABLE usp_segmentation_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    ordre_affichage INT NOT NULL DEFAULT 0,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_segmentation_code (code),
    UNIQUE KEY uk_usp_segmentation_libelle (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero_client VARCHAR(50) NOT NULL,
    nom_compte VARCHAR(255) NOT NULL,
    agence VARCHAR(100),
    region VARCHAR(150),
    email_principal VARCHAR(150),
    segmentation_id BIGINT,
    adresse VARCHAR(500),
    ville VARCHAR(100),
    commune VARCHAR(100),
    pays VARCHAR(100),
    statut VARCHAR(30) NOT NULL DEFAULT 'ACTIF',
    notes TEXT,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_client_numero (numero_client),
    KEY idx_usp_client_nom_compte (nom_compte),
    KEY idx_usp_client_agence (agence),
    KEY idx_usp_client_region (region),
    KEY idx_usp_client_segmentation (segmentation_id),
    CONSTRAINT fk_usp_client_segmentation FOREIGN KEY (segmentation_id) REFERENCES usp_segmentation_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    nom_complet VARCHAR(255) NOT NULL,
    fonction VARCHAR(150),
    telephone_principal VARCHAR(25),
    telephone_2 VARCHAR(25),
    numero_whatsapp VARCHAR(25),
    email VARCHAR(150),
    contact_principal BOOLEAN NOT NULL DEFAULT FALSE,
    consentement_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    date_consentement DATETIME,
    source_consentement VARCHAR(150),
    desabonne BOOLEAN NOT NULL DEFAULT FALSE,
    bloque BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_interaction DATETIME,
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_contact_client (client_id),
    KEY idx_usp_contact_telephone (telephone_principal),
    KEY idx_usp_contact_whatsapp (numero_whatsapp),
    KEY idx_usp_contact_email (email),
    CONSTRAINT fk_usp_contact_client FOREIGN KEY (client_id) REFERENCES usp_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(100) NOT NULL,
    couleur VARCHAR(20),
    description VARCHAR(300),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_tag_libelle (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_client_tag (client_id, tag_id),
    CONSTRAINT fk_usp_ct_client FOREIGN KEY (client_id) REFERENCES usp_client(id),
    CONSTRAINT fk_usp_ct_tag FOREIGN KEY (tag_id) REFERENCES usp_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_contact_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_contact_tag (contact_id, tag_id),
    CONSTRAINT fk_usp_cot_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id),
    CONSTRAINT fk_usp_cot_tag FOREIGN KEY (tag_id) REFERENCES usp_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----- Segmentations de référence (section 9.2 de la spec) -----
INSERT INTO usp_segmentation_client (code, libelle, description, ordre_affichage, actif, created_at) VALUES
 ('PLATINIUM', 'Platinium', NULL, 1, TRUE, NOW()),
 ('GOLD',      'Gold',      NULL, 2, TRUE, NOW()),
 ('SILVER',    'Silver',    NULL, 3, TRUE, NOW()),
 ('STANDARD',  'Standard',  NULL, 4, TRUE, NOW()),
 ('PROSPECT',  'Prospect',  NULL, 5, TRUE, NOW());
