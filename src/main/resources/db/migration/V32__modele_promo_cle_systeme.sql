-- =====================================================================
-- UbiSenderPro - Modèles de message « promotion » prédéfinis
-- Ajoute une clé système permettant de relier un modèle éditable à son rôle
-- (annonce mensuelle, lancement, derniers jours, dernière chance).
-- Les 4 modèles sont créés au démarrage (Bootstrap) s'ils sont absents.
-- =====================================================================

ALTER TABLE usp_modele_message ADD COLUMN cle_systeme VARCHAR(40) NULL;
