-- =====================================================================
-- UbiSenderPro - Phase 1 : Journal des imports (section 25.6 de la spec)
-- =====================================================================

CREATE TABLE usp_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom_fichier VARCHAR(255),
    type_import VARCHAR(50) NOT NULL,
    utilisateur_id BIGINT,
    mode_import VARCHAR(50),
    nb_lignes INT NOT NULL DEFAULT 0,
    nb_crees INT NOT NULL DEFAULT 0,
    nb_mis_a_jour INT NOT NULL DEFAULT 0,
    nb_ignores INT NOT NULL DEFAULT 0,
    nb_rejetes INT NOT NULL DEFAULT 0,
    duree_ms BIGINT,
    statut VARCHAR(30) NOT NULL DEFAULT 'EN_COURS',
    fichier_erreurs LONGTEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_import_type (type_import),
    KEY idx_usp_import_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_import_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_id BIGINT NOT NULL,
    numero_ligne INT NOT NULL,
    statut VARCHAR(30) NOT NULL,
    message VARCHAR(1000),
    contenu_ligne TEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_import_detail_import (import_id),
    CONSTRAINT fk_usp_import_detail FOREIGN KEY (import_id) REFERENCES usp_import(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_import_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    type_import VARCHAR(50) NOT NULL,
    mapping_json TEXT NOT NULL,
    separateur VARCHAR(5),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_import_mapping_nom (nom, type_import)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
