-- =====================================================================
-- UBIPHARM CRM - Desactivation des clients et verificateur de numeros
-- Un client desactive sort des campagnes et des listes sans etre archive ;
-- il se reactive. Le verificateur note si chaque numero est sur WhatsApp.
-- =====================================================================

ALTER TABLE crm_client DROP CONSTRAINT ck_crm_client_statut;
ALTER TABLE crm_client
    ADD CONSTRAINT ck_crm_client_statut CHECK (statut IN ('ACTIF', 'INACTIF', 'ARCHIVE')),
    ADD COLUMN desactive_le DATETIME NULL AFTER statut,
    ADD COLUMN desactive_par BIGINT NULL AFTER desactive_le,
    ADD COLUMN desactive_motif VARCHAR(255) NULL AFTER desactive_par,
    ADD CONSTRAINT fk_crm_client_desactive_par FOREIGN KEY (desactive_par) REFERENCES crm_utilisateur(id);

ALTER TABLE crm_contact
    ADD COLUMN whatsapp_present BOOLEAN NULL AFTER numero_whatsapp,
    ADD COLUMN whatsapp_verifie_le DATETIME NULL AFTER whatsapp_present;
