-- =====================================================================
-- UBIPHARM CRM - Module Marketing, lot 3 : catalogue des produits.
-- L'equipe Marketing importe un fichier Excel de produits ou les saisit a
-- la main. Un produit n'est jamais supprime : il est archive.
-- =====================================================================

CREATE TABLE crm_produit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    categorie VARCHAR(100),
    laboratoire VARCHAR(150),
    conditionnement VARCHAR(100),
    -- Prix public indicatif en FCFA ; vide si non communique
    prix DECIMAL(14, 2),
    description VARCHAR(1000),
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    import_id BIGINT,
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_produit_code (code),
    KEY idx_crm_produit_statut (statut, libelle),
    KEY idx_crm_produit_categorie (categorie),
    KEY idx_crm_produit_laboratoire (laboratoire),
    CONSTRAINT ck_crm_produit_statut CHECK (statut IN ('ACTIF', 'ARCHIVE')),
    CONSTRAINT ck_crm_produit_prix CHECK (prix IS NULL OR prix >= 0),
    CONSTRAINT fk_crm_produit_import FOREIGN KEY (import_id) REFERENCES crm_import (id),
    CONSTRAINT fk_crm_produit_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id),
    CONSTRAINT fk_crm_produit_modifie_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, 'Catalogue', m.id, d.ordre FROM (
    SELECT 'PRODUITS' code, 'Catalogue des produits' libelle, 'Consulter le catalogue des produits' description, 310 ordre
    UNION ALL SELECT 'PRODUITS_GERER', 'Gérer le catalogue', 'Importer, saisir, modifier et archiver les produits', 320
) d JOIN crm_module m ON m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP()
FROM crm_role r JOIN crm_droit d ON d.code IN ('PRODUITS', 'PRODUITS_GERER')
WHERE r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE', 'CHARGE_MARKETING');
