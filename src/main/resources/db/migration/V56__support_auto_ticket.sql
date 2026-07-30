-- =====================================================================
-- Centre de support — V56 : auto-ticket (anticipation des bugs)
--   Un bug jamais vu (exception Java / erreur JS, nouvelle signature) ouvre
--   automatiquement un ticket BUG + e-mail au support.
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'support.auto_ticket', 'true',
       'true = un ticket BUG est ouvert automatiquement à la 1re occurrence d''une erreur inattendue', 'SUPPORT', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'support.auto_ticket');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'support.auto_ticket_max_jour', '10',
       'Plafond d''auto-tickets par jour (anti-tempête)', 'SUPPORT', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'support.auto_ticket_max_jour');
