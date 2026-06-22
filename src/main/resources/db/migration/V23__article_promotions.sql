-- =====================================================================
-- UbiSenderPro - V23 : un article peut appartenir à plusieurs promotions
--   Table de liaison article <-> promotion (n..n).
--   (la colonne usp_article.promotion_id de V22 reste, inutilisée).
-- =====================================================================

CREATE TABLE usp_article_promotion (
    article_id BIGINT NOT NULL,
    promotion_id BIGINT NOT NULL,
    PRIMARY KEY (article_id, promotion_id),
    KEY idx_usp_artpromo_promo (promotion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
