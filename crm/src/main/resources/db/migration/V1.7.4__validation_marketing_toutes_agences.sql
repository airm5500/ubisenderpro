-- =====================================================================
-- UBIPHARM CRM - Validation des campagnes Marketing (decision d'Airman du 22/10/2026)
-- Le Marketing agit sur toutes les agences, mais un directeur d'agence ne valide par defaut que les
-- campagnes de ses agences ; le Directeur Marketing (vision globale) les valide toutes. Ce parametre,
-- desactive par defaut, permet au directeur d'agence de valider aussi celles qui visent d'autres agences.
-- =====================================================================
INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('marketing.validation_directeur_agence_toutes_agences', 'NON', 'OUI_NON', NULL, TRUE,
  'Un directeur d''agence du Marketing valide aussi les campagnes qui visent d''autres agences que les siennes (sinon elles se valident par le Directeur Marketing)',
  'MARKETING', 20, UTC_TIMESTAMP());
