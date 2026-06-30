-- =====================================================================
-- UbiSenderPro - Civilité du contact (Dr, Pr, M., Mme…)
-- Permet le repli « Docteur → formule générique » dans les modèles (variable {{civilite}}).
-- =====================================================================

ALTER TABLE usp_client_contact ADD COLUMN civilite VARCHAR(40) AFTER nom_complet;
