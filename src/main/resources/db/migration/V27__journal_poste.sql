-- =====================================================================
-- UbiSenderPro - Nom de poste (reverse-DNS) dans le journal d'actions
-- =====================================================================

ALTER TABLE usp_journal_action
    ADD COLUMN poste VARCHAR(255) NULL AFTER adresse_ip;
