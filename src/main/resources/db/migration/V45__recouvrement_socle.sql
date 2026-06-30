-- =====================================================================
-- UbiSenderPro - Module RECOUVREMENT (Lot 1 : socle financier)
-- Indépendant du Marketing. Réutilise la base clients (lien logique client_id
-- vers usp_client) SANS modifier la table Client. Tables 100% additives.
-- =====================================================================

-- Référentiels paramétrables du module (segments commerciaux, profils de
-- paiement, statuts de recouvrement). Même principe que les référentiels géo.
CREATE TABLE IF NOT EXISTS usp_rec_referentiel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- SEGMENT_COMMERCIAL | PROFIL_PAIEMENT | STATUT_RECOUVREMENT
    type VARCHAR(30) NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_rec_ref_type_libelle (type, libelle),
    UNIQUE KEY uk_usp_rec_ref_type_code (type, code),
    KEY idx_usp_rec_ref_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Fiche recouvrement : 1 par client suivi (complément financier).
CREATE TABLE IF NOT EXISTS usp_rec_fiche (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    segment_commercial VARCHAR(150),
    profil_paiement VARCHAR(150),
    responsable VARCHAR(150),
    statut VARCHAR(60),
    canal_prefere VARCHAR(20),
    observations TEXT,
    encours_initial DECIMAL(15,2) NOT NULL DEFAULT 0,
    date_situation DATE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_rec_fiche_client (client_id),
    KEY idx_usp_rec_fiche_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Créances : factures et avoirs (type), avec échéance.
CREATE TABLE IF NOT EXISTS usp_rec_creance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    -- FACTURE | AVOIR
    type VARCHAR(10) NOT NULL DEFAULT 'FACTURE',
    numero VARCHAR(60),
    date_emission DATE,
    date_echeance DATE,
    montant DECIMAL(15,2) NOT NULL DEFAULT 0,
    statut VARCHAR(30),
    notes VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_rec_creance_client (client_id),
    KEY idx_usp_rec_creance_echeance (date_echeance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Règlements (paiements).
CREATE TABLE IF NOT EXISTS usp_rec_paiement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    creance_id BIGINT,
    date_paiement DATE,
    montant DECIMAL(15,2) NOT NULL DEFAULT 0,
    mode VARCHAR(40),
    reference VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_rec_paiement_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Promesses de paiement.
CREATE TABLE IF NOT EXISTS usp_rec_promesse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    creance_id BIGINT,
    date_promesse DATE,
    montant DECIMAL(15,2) NOT NULL DEFAULT 0,
    -- EN_ATTENTE | TENUE | NON_TENUE
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    notes VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_rec_promesse_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
