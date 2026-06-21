-- =====================================================================
-- UbiSenderPro - V17 : icône d'application personnalisable (favicon)
--   Paramètre app.favicon : URL de l'icône (vide = icône par défaut)
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'app.favicon', '',
       'Icône personnalisée de l''application (URL ; vide = icône par défaut)', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'app.favicon');
