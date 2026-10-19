-- =====================================================================
-- UBIPHARM CRM - Campagnes automatiques (relances) et recurrentes (Marketing)
-- Une campagne validee une fois peut repartir seule chaque jour, chaque semaine
-- ou chaque mois jusqu'a une date de fin : a chaque envoi, ses messages sont
-- regeneres avec les factures (ou les clients) du moment.
-- =====================================================================

ALTER TABLE crm_campagne
    ADD COLUMN recurrence VARCHAR(20) NULL AFTER date_execution,
    ADD COLUMN fin_recurrence DATE NULL AFTER recurrence,
    ADD COLUMN debut_recurrence DATETIME NULL AFTER fin_recurrence,
    ADD COLUMN nb_executions INT NOT NULL DEFAULT 0 AFTER debut_recurrence,
    ADD CONSTRAINT ck_crm_campagne_recurrence CHECK (recurrence IN ('QUOTIDIENNE', 'HEBDOMADAIRE', 'MENSUELLE'));

-- Numero de l'envoi auquel appartient le message : une facture peut etre relancee a chaque envoi.
ALTER TABLE crm_campagne_message
    ADD COLUMN execution INT NOT NULL DEFAULT 1 AFTER campagne_id,
    ADD UNIQUE KEY uk_crm_campagne_message_execution (campagne_id, execution, facture_id),
    DROP INDEX uk_crm_campagne_message;
