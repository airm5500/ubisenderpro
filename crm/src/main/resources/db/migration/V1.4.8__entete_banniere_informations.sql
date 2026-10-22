-- =====================================================================
-- UBIPHARM CRM - En-tete et banniere des messages d'information (demande d'Airman du 20/10/2026)
-- L'en-tete des modeles d'information livres devient « 📢🚨 *UBI INFO · ...* 🚨📢 » ; un modele deja
-- retouche par le Marketing (en-tete different) n'est pas touche.
-- Une banniere (petite image) se depose dans Parametres > Charte, a cote du logo et de l'icone ;
-- elle part en tete des messages d'information qui n'ont pas d'affiche.
-- =====================================================================

UPDATE crm_modele_message
   SET contenu = CONCAT('📢🚨 ', SUBSTRING(SUBSTRING_INDEX(contenu, '\n', 1), 3), ' 🚨📢',
                        SUBSTRING(contenu, CHAR_LENGTH(SUBSTRING_INDEX(contenu, '\n', 1)) + 1)),
       updated_at = UTC_TIMESTAMP()
 WHERE type_campagne = 'INFORMATION' AND contenu LIKE '📢 *UBI INFO%';

ALTER TABLE crm_charte_fichier
    DROP CONSTRAINT ck_crm_charte_fichier_code,
    ADD CONSTRAINT ck_crm_charte_fichier_code CHECK (code IN ('LOGO', 'ICONE', 'BANNIERE'));

INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('marketing.banniere_informations', 'OUI', 'OUI_NON', NULL, TRUE,
  'Envoyer la bannière de la charte en tête des messages d''information sans affiche (WhatsApp et e-mail)', 'MARKETING', 7, UTC_TIMESTAMP());
