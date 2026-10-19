-- =====================================================================
-- UBIPHARM CRM - Suivi des creances : promesse de paiement, litige, contentieux
-- Le suivi est pose par l'agent (promesse, litige) ou la direction (contentieux)
-- et suspend les relances : une promesse jusqu'a la date promise, un litige ou un
-- contentieux jusqu'a ce qu'on le leve. Chaque changement est historise ; rien
-- n'est supprime.
-- =====================================================================

ALTER TABLE crm_facture
    ADD COLUMN suivi VARCHAR(20) NULL AFTER statut,
    ADD COLUMN suivi_date DATE NULL AFTER suivi,
    ADD COLUMN suivi_montant DECIMAL(14,2) NULL AFTER suivi_date,
    ADD COLUMN suivi_commentaire VARCHAR(500) NULL AFTER suivi_montant,
    ADD COLUMN suivi_par BIGINT NULL AFTER suivi_commentaire,
    ADD COLUMN suivi_le DATETIME NULL AFTER suivi_par,
    ADD CONSTRAINT ck_crm_facture_suivi CHECK (suivi IN ('PROMESSE', 'LITIGE', 'CONTENTIEUX')),
    ADD CONSTRAINT fk_crm_facture_suivi_par FOREIGN KEY (suivi_par) REFERENCES crm_utilisateur(id),
    ADD KEY idx_crm_facture_suivi (suivi, suivi_date);

-- Historique : une ligne par pose ou levee de suivi (suivi NULL = levee)
CREATE TABLE crm_facture_suivi (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facture_id BIGINT NOT NULL,
    suivi VARCHAR(20) NULL,
    suivi_date DATE NULL,
    suivi_montant DECIMAL(14,2) NULL,
    commentaire VARCHAR(500) NULL,
    cree_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_facture_suivi_facture (facture_id, created_at),
    CONSTRAINT fk_crm_fs_facture FOREIGN KEY (facture_id) REFERENCES crm_facture(id),
    CONSTRAINT fk_crm_fs_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_fs_suivi CHECK (suivi IN ('PROMESSE', 'LITIGE', 'CONTENTIEUX'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, 'Créances', m.id, d.ordre FROM (
    SELECT 'FACTURES_SUIVI' code, 'Promesses et litiges' libelle,
           'Noter une promesse de paiement ou un litige sur une facture, et le lever' description, 26 ordre
    UNION ALL SELECT 'FACTURES_CONTENTIEUX', 'Contentieux', 'Passer une facture au contentieux et l''en sortir', 27
) d JOIN crm_module m ON m.code = 'RECOUVREMENT';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON (
       r.code IN ('ADMIN', 'DIR_RECOUVREMENT') AND d.code IN ('FACTURES_SUIVI', 'FACTURES_CONTENTIEUX')
    OR r.code IN ('DIR_AGENCE', 'AGENT') AND d.code = 'FACTURES_SUIVI');
