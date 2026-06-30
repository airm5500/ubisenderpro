-- =====================================================================
-- UbiSenderPro - Module INFORMATIONS CLIENTS & ALERTES OPÉRATIONNELLES
-- Communications non promotionnelles (livraison, garde, fériés, horaires,
-- alertes, anniversaires). Alimente l'agrégateur Marketing (source = INFO).
-- =====================================================================

CREATE TABLE usp_info_evenement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    -- RETARD_LIVRAISON | MODIFICATION_TOURNEE | ANNULATION_TOURNEE | REPRISE_LIVRAISON
    -- | INFORMATION_GARDE | INFORMATION_JOUR_FERIE | MODIFICATION_HORAIRES
    -- | FERMETURE_AGENCE | INFORMATION_GENERALE | ALERTE_URGENTE | ANNIVERSAIRE_CLIENT
    type VARCHAR(40) NOT NULL,
    titre VARCHAR(200) NOT NULL,
    message TEXT,
    -- NORMALE | IMPORTANTE | URGENTE | CRITIQUE
    priorite VARCHAR(20) DEFAULT 'NORMALE',
    societe VARCHAR(150),
    agence VARCHAR(150),
    tournee VARCHAR(150),
    audience VARCHAR(40),
    segmentation_id BIGINT,
    liste_id BIGINT,
    canal VARCHAR(10),
    modele_id BIGINT,
    date_envoi DATETIME,
    date_fin_validite DATETIME,
    -- BROUILLON | EN_ATTENTE | PROGRAMMEE | EN_COURS | ENVOYEE | ANNULEE | EXPIREE | ECHOUEE | ARCHIVEE
    statut VARCHAR(20),
    responsable VARCHAR(150),
    cree_par VARCHAR(100),
    -- Détails livraison (§8)
    date_livraison DATE,
    creneau VARCHAR(100),
    heure_initiale VARCHAR(20),
    nouvelle_heure VARCHAR(20),
    cause_interne VARCHAR(255),
    cause_communicable VARCHAR(255),
    date_resolution DATE,
    -- Détails garde / jour férié (§9)
    jour_ferie VARCHAR(150),
    date_garde DATE,
    heure_limite_commande VARCHAR(20),
    consignes_livraison VARCHAR(500),
    pharmacien_garde VARCHAR(150),
    telephone_pharmacien VARCHAR(40),
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_info_code (code),
    KEY idx_usp_info_type (type),
    KEY idx_usp_info_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Lien proposition -> information (source INFO).
ALTER TABLE usp_envoi_propose ADD COLUMN info_id BIGINT AFTER evenement_id;

-- Anniversaires : naissance + consentement relationnel sur le contact (§10).
ALTER TABLE usp_client_contact
    ADD COLUMN jour_naissance INT AFTER civilite,
    ADD COLUMN mois_naissance INT AFTER jour_naissance,
    ADD COLUMN annee_naissance INT AFTER mois_naissance,
    ADD COLUMN consent_relationnel TINYINT(1) NOT NULL DEFAULT 0 AFTER annee_naissance;
