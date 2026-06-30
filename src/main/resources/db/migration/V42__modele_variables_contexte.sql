-- =====================================================================
-- UbiSenderPro - Modèles : variables de contexte figées à la validation
-- Stocke (JSON clé→valeur) les variables de campagne (mois_promotion,
-- date_debut, date_fin, avantage_ug…) afin de remplir les paramètres
-- d'un template Meta (canal API) non résolus par contact.
-- =====================================================================

ALTER TABLE usp_modele_message ADD COLUMN variables_contexte TEXT AFTER params_corps;
