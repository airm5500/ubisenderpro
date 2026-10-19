-- =====================================================================
-- UBIPHARM CRM - Module Marketing, lot 4 : promotions.
-- Une promotion = un code, un nom, une periode et des produits du catalogue
-- avec leur avantage (unites gratuites en quantite ou en %, avec plafond, ou
-- prix promotionnel). Une campagne Marketing cite une ou plusieurs promotions
-- actives : leurs variables se remplissent d'office. Une promotion depassee
-- passe d'office en terminee. Rien n'est supprime : on archive, on retire.
-- =====================================================================

CREATE TABLE crm_promotion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    -- BROUILLON : en preparation ; ACTIVE : proposee aux campagnes ; TERMINEE : periode depassee
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_promotion_code (code),
    KEY idx_crm_promotion_statut (statut, date_fin),
    CONSTRAINT ck_crm_promotion_statut CHECK (statut IN ('BROUILLON', 'ACTIVE', 'TERMINEE', 'ARCHIVEE')),
    CONSTRAINT ck_crm_promotion_dates CHECK (date_fin >= date_debut),
    CONSTRAINT fk_crm_promotion_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id),
    CONSTRAINT fk_crm_promotion_modifie_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un produit par promotion ; une ligne retiree est desactivee, jamais supprimee
CREATE TABLE crm_promotion_produit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT NOT NULL,
    produit_id BIGINT NOT NULL,
    quantite_min INT NOT NULL DEFAULT 1,
    -- UG_QUANTITE : ug_quantite unites offertes ; UG_POURCENTAGE : ug_pourcentage % de la quantite, ug_plafond au plus ;
    -- PRIX : prix_promo au lieu du prix public
    avantage VARCHAR(20) NOT NULL,
    ug_quantite INT,
    ug_pourcentage DECIMAL(5, 2),
    ug_plafond INT,
    prix_promo DECIMAL(14, 2),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    import_id BIGINT,
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_promotion_produit (promotion_id, produit_id),
    KEY idx_crm_promotion_produit_produit (produit_id),
    CONSTRAINT ck_crm_promotion_produit_avantage CHECK (avantage IN ('UG_QUANTITE', 'UG_POURCENTAGE', 'PRIX')),
    CONSTRAINT ck_crm_promotion_produit_quantite CHECK (quantite_min >= 1),
    CONSTRAINT fk_crm_promotion_produit_promotion FOREIGN KEY (promotion_id) REFERENCES crm_promotion (id),
    CONSTRAINT fk_crm_promotion_produit_produit FOREIGN KEY (produit_id) REFERENCES crm_produit (id),
    CONSTRAINT fk_crm_promotion_produit_import FOREIGN KEY (import_id) REFERENCES crm_import (id),
    CONSTRAINT fk_crm_promotion_produit_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id),
    CONSTRAINT fk_crm_promotion_produit_modifie_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Promotions citees par une campagne Marketing (identifiants separes par des virgules)
ALTER TABLE crm_campagne ADD COLUMN promotion_ids VARCHAR(500) AFTER liste_id;

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('promotion_nom',      'Nom de la promotion',            'calcul.promotion_nom',      'Promo santé de novembre', UTC_TIMESTAMP()),
 ('promotion_code',     'Code de la promotion',           'calcul.promotion_code',     'NOV26',                   UTC_TIMESTAMP()),
 ('promotion_debut',    'Début de la promotion',          'calcul.promotion_debut',    '01/11/2026',              UTC_TIMESTAMP()),
 ('promotion_fin',      'Fin de la promotion',            'calcul.promotion_fin',      '30/11/2026',              UTC_TIMESTAMP()),
 ('produits_promotion', 'Produits et avantages (liste)',  'calcul.produits_promotion', '• Doliprane 500 mg : 2 offerts pour 10 achetés', UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, 'Catalogue', m.id, d.ordre FROM (
    SELECT 'PROMOTIONS' code, 'Promotions' libelle, 'Consulter les promotions et leurs produits' description, 330 ordre
    UNION ALL SELECT 'PROMOTIONS_GERER', 'Gérer les promotions', 'Créer, activer, archiver les promotions ; saisir et importer leurs produits', 340
) d JOIN crm_module m ON m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP()
FROM crm_role r JOIN crm_droit d ON d.code IN ('PROMOTIONS', 'PROMOTIONS_GERER')
WHERE r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE', 'CHARGE_MARKETING');
