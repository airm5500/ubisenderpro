-- =====================================================================
-- UbiSenderPro - V10 : mode test par compte WhatsApp.
-- Quand mode_test = TRUE, les envois sont simulés (aucun appel à Meta) :
-- permet de valider l'application sans accès à la WhatsApp Cloud API.
-- =====================================================================

ALTER TABLE usp_whatsapp_account
    ADD COLUMN mode_test BOOLEAN NOT NULL DEFAULT FALSE AFTER actif;
