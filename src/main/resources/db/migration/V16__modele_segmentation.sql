-- =====================================================================
-- UbiSenderPro - V16 : segmentation dédiée d'un modèle + paramètres société
--   - usp_modele_message.segmentation_id : modèle ciblant une segmentation
--   - paramètres app.societe_tel / app.site / app.lien_commande
--     (variables de message [TEL_SOCIETE] [SITE] [LIEN_COMMANDE])
-- =====================================================================

ALTER TABLE usp_modele_message
    ADD COLUMN segmentation_id BIGINT NULL AFTER nom_modele_whatsapp;

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'app.societe_tel', '',
       'Téléphone(s) société, séparés par ; (variable [TEL_SOCIETE])', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'app.societe_tel');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'app.site', '',
       'Lien du site société (variable [SITE])', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'app.site');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'app.lien_commande', '',
       'Lien de commande société (variable [LIEN_COMMANDE])', 'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'app.lien_commande');
