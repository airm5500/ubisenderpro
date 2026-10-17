-- =====================================================================
-- UBIPHARM CRM - Imports avec simulation
-- Chaque import est d'abord simule : chaque ligne est evaluee et gardee
-- avec ses donnees. L'utilisateur valide ensuite, et la validation
-- re-evalue les lignes contre l'etat courant de la base.
-- =====================================================================

CREATE TABLE crm_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(30) NOT NULL,
    nom_fichier VARCHAR(255) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'SIMULE',
    nb_lignes INT NOT NULL DEFAULT 0,
    nb_creations INT NOT NULL DEFAULT 0,
    nb_mises_a_jour INT NOT NULL DEFAULT 0,
    nb_identiques INT NOT NULL DEFAULT 0,
    nb_rejets INT NOT NULL DEFAULT 0,
    -- resultat de la validation
    nb_crees INT,
    nb_mis_a_jour INT,
    nb_ignores INT,
    utilisateur_id BIGINT NOT NULL,
    valide_par BIGINT,
    valide_le DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_import_type_date (type, created_at),
    CONSTRAINT fk_crm_import_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_import_statut CHECK (statut IN ('SIMULE', 'VALIDE', 'ANNULE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_import_ligne (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_id BIGINT NOT NULL,
    numero_ligne INT NOT NULL,
    cle VARCHAR(100),
    action VARCHAR(20) NOT NULL,
    message VARCHAR(2000),
    donnees TEXT NOT NULL,
    -- resultat a la validation : CREE, MIS_A_JOUR, IGNORE, REJETE
    resultat VARCHAR(20),
    PRIMARY KEY (id),
    KEY idx_crm_il_import (import_id, action, numero_ligne),
    CONSTRAINT fk_crm_il_import FOREIGN KEY (import_id) REFERENCES crm_import(id),
    CONSTRAINT ck_crm_il_action CHECK (action IN ('CREATION', 'MISE_A_JOUR', 'IDENTIQUE', 'REJET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('import.lignes_max', '20000', 'Nombre maximal de lignes par fichier importe', 'IMPORT', UTC_TIMESTAMP());
