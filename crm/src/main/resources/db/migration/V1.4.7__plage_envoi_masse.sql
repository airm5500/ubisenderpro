-- =====================================================================
-- UBIPHARM CRM - Plage horaire propre aux envois WhatsApp en masse (demande d'Airman du 19/10/2026)
-- Les envois en masse ne suivent plus la plage des relances : par defaut ils partent a toute heure,
-- toujours au debit des relances et avec le plafond journalier du numero.
-- =====================================================================

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('whatsapp.envoi_masse.heure_debut', '0', 'HEURE', 0, 24, TRUE,
  'Heure (Abidjan) à partir de laquelle les envois en masse partent (0 = à toute heure)', 'WHATSAPP', 22, UTC_TIMESTAMP()),
 ('whatsapp.envoi_masse.heure_fin', '24', 'HEURE', 0, 24, TRUE,
  'Heure (Abidjan) après laquelle plus aucun envoi en masse ne part (24 = jusqu''à minuit)', 'WHATSAPP', 23, UTC_TIMESTAMP());
