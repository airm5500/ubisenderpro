-- =====================================================================
-- UbiSenderPro - V20 : pièces jointes multiples pour l'envoi de masse
--   medias_json : tableau JSON [{url,type,mime,nom}] (le média unique
--   media_url/... reste pris en charge pour compatibilité ascendante)
-- =====================================================================

ALTER TABLE usp_wa_bulk_job
    ADD COLUMN medias_json TEXT NULL AFTER media_nom;
