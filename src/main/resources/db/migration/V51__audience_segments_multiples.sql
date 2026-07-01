-- Sélection multiple de segments (CSV d'IDs de segmentation) pour le ciblage
-- des informations clients et des événements de disponibilité.
ALTER TABLE usp_info_evenement
    ADD COLUMN segmentation_ids VARCHAR(255) NULL;
ALTER TABLE usp_dispo_evenement
    ADD COLUMN segmentation_ids VARCHAR(255) NULL;
