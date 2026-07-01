-- =====================================================================
-- UbiSenderPro - V53 : délai de déconnexion par inactivité (minutes)
--   Paramètre delai_deconnexion : durée d'inactivité avant expiration de
--   la session (côté serveur et client). Défaut : 60 minutes.
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'delai_deconnexion', '60',
       'Durée d''inactivité (en minutes) avant déconnexion automatique', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'delai_deconnexion');
