-- =====================================================================
-- UBIPHARM CRM - Sections du menu de gauche repliables (demande d'Airman du 20/10/2026)
-- OUI : chaque titre de section (Echanges, Administration...) plie ou deplie sa section, et en ouvrir une ferme
-- l'autre. Desactive par defaut : toutes les sections restent ouvertes, comme avant.
-- =====================================================================

INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('charte.menu_repliable', 'NON', 'OUI_NON', NULL, TRUE,
  'Sections du menu de gauche repliables : un clic sur un titre ouvre sa section et ferme celle qui était ouverte', 'CHARTE', 5, UTC_TIMESTAMP());
