-- =====================================================================
-- WhatsApp Web — V57 : santé de réception d'une session
--   Une session peut être « ouverte » (envoi OK) mais recevoir les réponses
--   illisibles (session de chiffrement désynchronisée après une coupure).
--   On distingue donc la SANTE (OK / DEGRADED) du STATUT (connexion),
--   et on horodate le dernier message entrant lisible (repère de confiance).
-- =====================================================================

ALTER TABLE usp_wa_web_session
    ADD COLUMN sante VARCHAR(20) NOT NULL DEFAULT 'OK' AFTER statut;

ALTER TABLE usp_wa_web_session
    ADD COLUMN sante_detail VARCHAR(255) NULL AFTER sante;

ALTER TABLE usp_wa_web_session
    ADD COLUMN dernier_entrant_le DATETIME NULL AFTER sante_detail;
