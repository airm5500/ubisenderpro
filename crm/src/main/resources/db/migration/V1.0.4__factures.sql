-- =====================================================================
-- UBIPHARM CRM - Module Recouvrement : factures
-- Le sous-type de compte est porte par la facture. Le solde restant du
-- est fourni par l'import et fait foi.
-- =====================================================================

CREATE TABLE crm_sous_type_compte (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_stc_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_facture (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(50) NOT NULL,
    client_id BIGINT NOT NULL,
    sous_type_id BIGINT NOT NULL,
    nature VARCHAR(10) NOT NULL DEFAULT 'FACTURE',
    date_facture DATE NOT NULL,
    date_echeance DATE NOT NULL,
    montant DECIMAL(15,2) NOT NULL,
    solde DECIMAL(15,2) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'A_RELANCER',
    import_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_facture_reference (reference),
    KEY idx_crm_facture_client (client_id, statut),
    KEY idx_crm_facture_echeance (date_echeance, statut),
    CONSTRAINT fk_crm_facture_client FOREIGN KEY (client_id) REFERENCES crm_client(id),
    CONSTRAINT fk_crm_facture_sous_type FOREIGN KEY (sous_type_id) REFERENCES crm_sous_type_compte(id),
    CONSTRAINT fk_crm_facture_import FOREIGN KEY (import_id) REFERENCES crm_import(id),
    CONSTRAINT ck_crm_facture_nature CHECK (nature IN ('FACTURE', 'AVOIR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Valeurs citees dans le cahier des charges ; la liste complete reste a fournir par UBIPHARM.
INSERT INTO crm_sous_type_compte (code, libelle, created_at) VALUES
 ('NORMAL',              'Normal',                 UTC_TIMESTAMP()),
 ('RENFORCEMENT_STOCK',  'Renforcement de stock',  UTC_TIMESTAMP());
