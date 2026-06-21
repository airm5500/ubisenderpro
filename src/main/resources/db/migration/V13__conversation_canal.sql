-- =====================================================================
-- UbiSenderPro - V13 : conversations multi-canal (API officielle / WhatsApp Web)
-- + ciblage des campagnes par segmentation client.
-- =====================================================================

ALTER TABLE usp_conversation
    ADD COLUMN canal VARCHAR(10) NOT NULL DEFAULT 'API' AFTER id;

ALTER TABLE usp_conversation
    ADD COLUMN wa_web_session_id BIGINT AFTER whatsapp_account_id;

-- Le compte API devient optionnel (les conversations WhatsApp Web n'en ont pas).
ALTER TABLE usp_conversation
    MODIFY COLUMN whatsapp_account_id BIGINT NULL;

-- Campagnes : ciblage direct par segmentation client.
ALTER TABLE usp_campagne
    ADD COLUMN segmentation_id BIGINT AFTER segment_id;
