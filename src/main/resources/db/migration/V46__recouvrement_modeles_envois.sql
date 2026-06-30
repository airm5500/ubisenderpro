-- =====================================================================
-- UbiSenderPro - Module RECOUVREMENT (Lot 2 : modèles + envois)
-- Modèles de relance (variables finance, compatibles templates Meta) et
-- historique des envois. Additif, indépendant du Marketing.
-- =====================================================================

CREATE TABLE IF NOT EXISTS usp_rec_modele (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(150) NOT NULL,
    -- RELANCE_PREVENTIVE | FACTURE_ECHUE | IMPAYE | MISE_EN_DEMEURE | DIVERS
    type VARCHAR(30) NOT NULL DEFAULT 'DIVERS',
    -- WHATSAPP | EMAIL | TOUS
    canal VARCHAR(20) NOT NULL DEFAULT 'TOUS',
    sujet VARCHAR(255),
    corps TEXT NOT NULL,
    nom_modele_whatsapp VARCHAR(150),
    params_corps VARCHAR(500),
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_rec_modele_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usp_rec_envoi (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    modele_id BIGINT,
    canal VARCHAR(20) NOT NULL,
    destinataire VARCHAR(255),
    sujet VARCHAR(255),
    message TEXT,
    -- ENVOYE | ECHOUE
    statut VARCHAR(20) NOT NULL,
    erreur VARCHAR(500),
    wa_message_id VARCHAR(120),
    cree_par VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_rec_envoi_client (client_id),
    KEY idx_usp_rec_envoi_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
