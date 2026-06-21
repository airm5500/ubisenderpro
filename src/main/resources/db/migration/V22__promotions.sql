-- =====================================================================
-- UbiSenderPro - V22 : table des promotions + lien article -> promotion
--   Une promotion (code, nom, dates) est définie une fois puis associée
--   à plusieurs produits (au lieu de re-saisir sur chaque produit).
-- =====================================================================

CREATE TABLE usp_promotion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    date_debut DATETIME,
    date_fin DATETIME,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_promotion_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE usp_article ADD COLUMN promotion_id BIGINT NULL;
CREATE INDEX idx_usp_article_promotion ON usp_article (promotion_id);
