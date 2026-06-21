-- =====================================================================
-- UbiSenderPro - V12 : paramètre « mode d'envoi » (API officielle / WhatsApp Web)
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'whatsapp.mode_envoi', 'API',
       'Mode d''envoi par défaut : API (Cloud officielle) ou WEB (WhatsApp Web)',
       'WHATSAPP', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'whatsapp.mode_envoi');
