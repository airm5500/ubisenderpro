-- =====================================================================
-- UBIPHARM CRM - Nom du pharmacien et segments clients
-- Precisions d'UBIPHARM du 17/10/2026 : la colonne Adresse1 du fichier
-- clients contient le nom du pharmacien ; Seg vaut P, B ou S.
-- =====================================================================

ALTER TABLE crm_client ADD COLUMN nom_pharmacien VARCHAR(255) AFTER raison_sociale;

CREATE TABLE crm_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_segment_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_segment (code, libelle, ordre, created_at) VALUES
 ('P', 'Platinum', 1, UTC_TIMESTAMP()),
 ('S', 'Silver',   2, UTC_TIMESTAMP()),
 ('B', 'Bronze',   3, UTC_TIMESTAMP());
