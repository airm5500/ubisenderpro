-- =====================================================================
-- UbiSenderPro - Audiences (§16) : ciblage par segmentation(s)
-- La campagne mémorise l'audience choisie et les segmentations résolues.
-- =====================================================================

ALTER TABLE usp_campagne
    ADD COLUMN audience VARCHAR(40) AFTER segmentation_id,
    ADD COLUMN segmentation_ids VARCHAR(255) AFTER audience;

ALTER TABLE usp_envoi_propose
    ADD COLUMN audience VARCHAR(40) AFTER segment_id;
