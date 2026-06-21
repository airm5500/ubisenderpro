-- =====================================================================
-- UbiSenderPro - V21 : catalogue produit (champs promo, UG, prix public)
--   - code_article renommé en pscode (code interne)
--   - prix de vente public, quantité commandée, quantité UG
--   - nom de promo, code de promo (dates promo : champs existants
--     date_debut_promotion / date_fin_promotion)
-- =====================================================================

ALTER TABLE usp_article
    CHANGE COLUMN code_article pscode VARCHAR(50) NOT NULL;

ALTER TABLE usp_article
    ADD COLUMN prix_vente_public DECIMAL(15,2) NULL AFTER prix_vente,
    ADD COLUMN quantite_commandee INT NULL,
    ADD COLUMN quantite_ug INT NULL,
    ADD COLUMN nom_promo VARCHAR(150) NULL,
    ADD COLUMN code_promo VARCHAR(50) NULL;

CREATE INDEX idx_usp_article_code_promo ON usp_article (code_promo);
