-- =====================================================================
-- UbiSenderPro - Référentiels géographiques
-- Valeurs contrôlées pour pays / régions / villes / communes / agences,
-- sélectionnables par liste déroulante et auto-créées à l'import.
-- Table unique avec discriminant « type ».
-- =====================================================================

CREATE TABLE usp_referentiel_geo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- PAYS | REGION | VILLE | COMMUNE | AGENCE
    type VARCHAR(20) NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_refgeo_type_libelle (type, libelle),
    UNIQUE KEY uk_usp_refgeo_type_code (type, code),
    KEY idx_usp_refgeo_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Valeurs par défaut demandées : pays Côte d'Ivoire, ville Abidjan.
INSERT INTO usp_referentiel_geo (type, code, libelle, actif, created_at)
VALUES ('PAYS', 'PAYS-DEFAUT', 'Côte d''Ivoire', 1, NOW()),
       ('VILLE', 'VILLE-DEFAUT', 'Abidjan', 1, NOW());
