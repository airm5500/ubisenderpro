-- =====================================================================
-- UbiSenderPro - Campagnes : canal d'envoi (API/WEB) + relance des échecs
-- =====================================================================

-- Canal d'envoi de la campagne : 'API' (Cloud API officielle) ou 'WEB' (WhatsApp Web).
ALTER TABLE usp_campagne
    ADD COLUMN canal VARCHAR(10) NOT NULL DEFAULT 'API';

-- Session WhatsApp Web utilisée lorsque canal = 'WEB'.
ALTER TABLE usp_campagne
    ADD COLUMN wa_web_session_id VARCHAR(80);

-- Nombre de tentatives d'envoi par destinataire (conserve l'historique lors des relances).
ALTER TABLE usp_campagne_destinataire
    ADD COLUMN tentatives INT NOT NULL DEFAULT 0;
