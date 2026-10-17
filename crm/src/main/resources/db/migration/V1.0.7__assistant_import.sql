-- =====================================================================
-- UBIPHARM CRM - Assistant d'import
-- Lettres d'agence confirmees par UBIPHARM le 17/10/2026 ; modeles de
-- correspondance de colonnes reutilisables d'un import a l'autre.
-- =====================================================================

UPDATE crm_agence SET code_import = 'B' WHERE code = 'BKE';
UPDATE crm_agence SET code_import = 'G' WHERE code = 'GAG';
UPDATE crm_agence SET code_import = 'V' WHERE code = 'VRI';
UPDATE crm_agence SET code_import = 'Y' WHERE code = 'YOP';

CREATE TABLE crm_import_modele (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(30) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    correspondance TEXT NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    utilisateur_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_import_modele (type, nom),
    CONSTRAINT fk_crm_import_modele_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_import_modele_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
