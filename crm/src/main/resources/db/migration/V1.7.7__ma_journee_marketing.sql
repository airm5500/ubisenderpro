-- =====================================================================
-- UBIPHARM CRM - « Ma journée Marketing », page d'arrivee du module (remarque 10 du 22/10/2026) :
-- horizon des promotions qui commencent ou finissent et des evenements a venir.
-- =====================================================================
INSERT INTO crm_parametre (cle, valeur, type_valeur, options, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('marketing.journee_horizon_jours', '7', 'ENTIER', NULL, 0, 60, TRUE,
  'Ma journée Marketing : nombre de jours à venir pour les promotions qui commencent ou finissent et les événements',
  'MARKETING', 30, UTC_TIMESTAMP());
