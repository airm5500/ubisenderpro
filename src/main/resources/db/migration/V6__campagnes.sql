-- =====================================================================
-- UbiSenderPro - Phase 2 : Listes, segments, campagnes
-- =====================================================================

CREATE TABLE usp_liste_diffusion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_liste_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_liste_diffusion_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    liste_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    source VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_liste_contact (liste_id, contact_id),
    KEY idx_usp_liste_contact_liste (liste_id),
    CONSTRAINT fk_usp_lc_liste FOREIGN KEY (liste_id) REFERENCES usp_liste_diffusion(id),
    CONSTRAINT fk_usp_lc_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    logique VARCHAR(5) NOT NULL DEFAULT 'ET',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_segment_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_segment_filtre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    segment_id BIGINT NOT NULL,
    critere VARCHAR(60) NOT NULL,
    operateur VARCHAR(20) NOT NULL DEFAULT 'EGAL',
    valeur VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_segment_filtre (segment_id),
    CONSTRAINT fk_usp_segment_filtre FOREIGN KEY (segment_id) REFERENCES usp_segment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_campagne (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    objectif VARCHAR(255),
    categorie VARCHAR(60),
    responsable_id BIGINT,
    whatsapp_account_id BIGINT,
    modele_id BIGINT,
    liste_id BIGINT,
    segment_id BIGINT,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    date_programmee DATETIME,
    fuseau_horaire VARCHAR(60),
    nb_destinataires INT NOT NULL DEFAULT 0,
    nb_envoyes INT NOT NULL DEFAULT 0,
    nb_distribues INT NOT NULL DEFAULT 0,
    nb_lus INT NOT NULL DEFAULT 0,
    nb_repondus INT NOT NULL DEFAULT 0,
    nb_echoues INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_campagne_statut (statut),
    KEY idx_usp_campagne_date (date_programmee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_campagne_destinataire (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campagne_id BIGINT NOT NULL,
    contact_id BIGINT,
    numero_whatsapp VARCHAR(25),
    nom_contact VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    wa_message_id VARCHAR(150),
    erreur VARCHAR(500),
    envoye_at DATETIME,
    distribue_at DATETIME,
    lu_at DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_camp_dest_campagne (campagne_id),
    KEY idx_usp_camp_dest_statut (statut),
    CONSTRAINT fk_usp_camp_dest FOREIGN KEY (campagne_id) REFERENCES usp_campagne(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
