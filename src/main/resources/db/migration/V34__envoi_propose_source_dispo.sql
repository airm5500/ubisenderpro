-- =====================================================================
-- UbiSenderPro - Propositions d'envoi : source multiple (Promo / Dispo)
-- Marketing devient l'agrégateur commun (promotions + disponibilités/ruptures).
-- =====================================================================

ALTER TABLE usp_envoi_propose
    ADD COLUMN source VARCHAR(10) DEFAULT 'PROMO' AFTER type,
    ADD COLUMN evenement_id BIGINT AFTER promotion_id;

UPDATE usp_envoi_propose SET source = 'PROMO' WHERE source IS NULL;
