-- =====================================================================
-- UbiSenderPro - Modèles Meta : paramètres du corps (mapping {{1}},{{2}}…)
-- Liste ordonnée de variables (CSV) injectées dans les paramètres du template.
-- =====================================================================

ALTER TABLE usp_modele_message ADD COLUMN params_corps VARCHAR(500) AFTER nom_modele_whatsapp;
