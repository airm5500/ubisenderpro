-- =====================================================================
-- UbiSenderPro - V60 : adresse de la société
--   Complète app.societe / app.societe_tel / app.site pour composer
--   l'en-tête des impressions (export PDF des grilles).
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'app.adresse', '',
       'Adresse postale de la société (en-tête des impressions PDF)', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'app.adresse');
