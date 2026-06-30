-- =====================================================================
-- UbiSenderPro - Module Promotions (P1) : statuts + produits promotionnels
-- Additif : n'altère pas le lien Article<->Promotion existant.
-- =====================================================================

-- Enrichissement de la promotion (statut auto + responsable + créateur).
ALTER TABLE usp_promotion
    ADD COLUMN statut VARCHAR(20) AFTER actif,
    ADD COLUMN responsable VARCHAR(150) AFTER statut,
    ADD COLUMN cree_par VARCHAR(100) AFTER responsable;

-- Statut initial déduit des dates pour les promotions existantes.
UPDATE usp_promotion
   SET statut = CASE
       WHEN date_debut IS NOT NULL AND date_debut > NOW() THEN 'PROGRAMMEE'
       WHEN date_fin   IS NOT NULL AND date_fin   < NOW() THEN 'INACTIVE'
       ELSE 'ACTIVE'
   END
 WHERE statut IS NULL;

-- Produits d'une promotion avec leurs conditions d'unités gratuites (UG).
CREATE TABLE usp_promotion_produit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT NOT NULL,
    article_id BIGINT,
    cip7 VARCHAR(20),
    cip13 VARCHAR(20),
    nom_produit VARCHAR(255),
    quantite_minimale INT,
    taux_ug DECIMAL(7,2),
    taux_max_ug DECIMAL(7,2),
    quantite_ug INT,
    quantite_ug_max INT,
    mode_calcul VARCHAR(20),
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_promo_produit_promo (promotion_id),
    CONSTRAINT fk_usp_promo_produit FOREIGN KEY (promotion_id) REFERENCES usp_promotion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
