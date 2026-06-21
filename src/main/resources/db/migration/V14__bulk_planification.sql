-- =====================================================================
-- UbiSenderPro - V14 : planification horaire des envois en masse
-- date de démarrage + plage horaire autorisée (anti-ban, lots planifiés).
-- =====================================================================

ALTER TABLE usp_wa_bulk_job
    ADD COLUMN date_programmee DATETIME AFTER statut;

ALTER TABLE usp_wa_bulk_job
    ADD COLUMN heure_debut INT NOT NULL DEFAULT 0 AFTER date_programmee;

ALTER TABLE usp_wa_bulk_job
    ADD COLUMN heure_fin INT NOT NULL DEFAULT 0 AFTER heure_debut;
