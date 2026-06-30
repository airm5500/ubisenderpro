-- =====================================================================
-- UbiSenderPro - Module DISPONIBILITÉS & RUPTURES (Phase R1 : socle)
-- Événements (disponibilité, retour de rupture, risque, rupture, stock limité)
-- et produits concernés. Alimentera le calendrier marketing en phases suivantes.
-- =====================================================================

CREATE TABLE usp_dispo_evenement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    -- ANNONCE_DISPONIBILITE | RETOUR_RUPTURE | RISQUE_RUPTURE | RUPTURE_CONFIRMEE | STOCK_LIMITE
    type VARCHAR(30) NOT NULL,
    titre VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    date_debut DATETIME,
    date_fin DATETIME,
    agence VARCHAR(150),
    societe VARCHAR(150),
    -- TOUS_LES_SEGMENTS | DIAMOND | PLATINIUM | DIAMOND_ET_PLATINIUM | SEGMENTS_SELECTIONNES | ...
    audience VARCHAR(40),
    segmentation_id BIGINT,
    canal VARCHAR(10),
    modele_id BIGINT,
    -- BROUILLON | PROGRAMMEE | ENVOYEE | ANNULEE | ARCHIVEE
    statut VARCHAR(20),
    responsable VARCHAR(150),
    cree_par VARCHAR(100),
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_dispo_evt_code (code),
    KEY idx_usp_dispo_evt_type (type),
    KEY idx_usp_dispo_evt_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_dispo_produit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evenement_id BIGINT NOT NULL,
    article_id BIGINT,
    cip7 VARCHAR(20),
    cip13 VARCHAR(20),
    nom_produit VARCHAR(255),
    quantite_disponible INT,
    seuil_rupture INT,
    couverture_jours INT,
    date_peremption DATE,
    numero_lot VARCHAR(50),
    agence VARCHAR(150),
    stock_limite TINYINT(1) NOT NULL DEFAULT 0,
    lien_reservation VARCHAR(500),
    -- DISPONIBLE | STOCK_LIMITE | RISQUE_RUPTURE | EN_RUPTURE | RETOUR_RUPTURE | INACTIF | ARCHIVE
    statut VARCHAR(20),
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_dispo_prod_evt (evenement_id),
    CONSTRAINT fk_usp_dispo_prod_evt FOREIGN KEY (evenement_id) REFERENCES usp_dispo_evenement(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
