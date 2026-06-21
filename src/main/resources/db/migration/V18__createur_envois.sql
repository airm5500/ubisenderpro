-- =====================================================================
-- UbiSenderPro - V18 : émetteur (créateur) des campagnes et envois de masse
--   Permet d'afficher « qui a envoyé » dans l'historique global.
-- =====================================================================

ALTER TABLE usp_campagne
    ADD COLUMN cree_par BIGINT NULL AFTER created_at;

ALTER TABLE usp_wa_bulk_job
    ADD COLUMN cree_par BIGINT NULL AFTER created_at;
