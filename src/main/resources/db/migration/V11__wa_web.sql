-- =====================================================================
-- UbiSenderPro - V11 : canal WhatsApp Web (non officiel) + envoi en masse
-- Sessions (scan QR via le service compagnon Baileys), travaux d'envoi en
-- masse (5 variantes, pièce jointe, débit) et leurs destinataires.
-- =====================================================================

CREATE TABLE usp_wa_web_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(150) NOT NULL,
    numero VARCHAR(30),
    statut VARCHAR(20) NOT NULL DEFAULT 'DECONNECTE',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_wa_bulk_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    nom VARCHAR(150),
    msg1 TEXT,
    msg2 TEXT,
    msg3 TEXT,
    msg4 TEXT,
    msg5 TEXT,
    media_url VARCHAR(1000),
    media_type VARCHAR(20),
    media_mime VARCHAR(100),
    media_nom VARCHAR(255),
    attente_min INT NOT NULL DEFAULT 4,
    attente_max INT NOT NULL DEFAULT 8,
    pause_apres INT NOT NULL DEFAULT 10,
    pause_min INT NOT NULL DEFAULT 10,
    pause_max INT NOT NULL DEFAULT 20,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    total INT NOT NULL DEFAULT 0,
    envoyes INT NOT NULL DEFAULT 0,
    echoues INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_bulk_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_wa_bulk_destinataire (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    numero VARCHAR(30) NOT NULL,
    nom VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    erreur VARCHAR(500),
    sent_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_bulk_dest_job (job_id),
    CONSTRAINT fk_usp_bulk_dest_job FOREIGN KEY (job_id) REFERENCES usp_wa_bulk_job(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
