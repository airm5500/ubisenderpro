-- =====================================================================
-- UbiSenderPro - V24 : nettoyage — suppression de usp_article.promotion_id
--   Remplacé par la liaison n..n usp_article_promotion (V23).
-- =====================================================================

ALTER TABLE usp_article DROP INDEX idx_usp_article_promotion;
ALTER TABLE usp_article DROP COLUMN promotion_id;
