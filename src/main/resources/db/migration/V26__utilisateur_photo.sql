-- =====================================================================
-- UbiSenderPro - Photo de profil de l'utilisateur (data URI base64)
-- =====================================================================

ALTER TABLE usp_utilisateur
    ADD COLUMN photo LONGTEXT NULL AFTER avatar;
