-- =====================================================================
-- UbiSenderPro - V8 : média d'en-tête des modèles de message
-- =====================================================================

ALTER TABLE usp_modele_message
    ADD COLUMN entete_media_url VARCHAR(1000) AFTER entete_media_type;
