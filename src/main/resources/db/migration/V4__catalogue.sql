-- =====================================================================
-- UbiSenderPro - Phase 2 : Catalogue (articles, catégories, marques, stock)
-- =====================================================================

CREATE TABLE usp_categorie_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    parent_id BIGINT,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_categorie_code (code),
    KEY idx_usp_categorie_libelle (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_marque (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    logo_url VARCHAR(1000),
    logo_local VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_marque_code (code),
    UNIQUE KEY uk_usp_marque_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code_article VARCHAR(50) NOT NULL,
    code_barres VARCHAR(50),
    cip VARCHAR(50),
    designation VARCHAR(255) NOT NULL,
    description_courte VARCHAR(500),
    description_complete TEXT,
    categorie_id BIGINT,
    marque_id BIGINT,
    prix_vente DECIMAL(15,2) NOT NULL DEFAULT 0,
    prix_promotionnel DECIMAL(15,2),
    date_debut_promotion DATETIME,
    date_fin_promotion DATETIME,
    stock_disponible DECIMAL(15,3) NOT NULL DEFAULT 0,
    seuil_alerte DECIMAL(15,3) NOT NULL DEFAULT 0,
    unite VARCHAR(30),
    image_url VARCHAR(1000),
    image_locale VARCHAR(500),
    lien_produit VARCHAR(1000),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    publiable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_article_code (code_article),
    KEY idx_usp_article_designation (designation),
    KEY idx_usp_article_cip (cip),
    KEY idx_usp_article_barcode (code_barres),
    KEY idx_usp_article_categorie (categorie_id),
    KEY idx_usp_article_marque (marque_id),
    CONSTRAINT fk_usp_article_categorie FOREIGN KEY (categorie_id) REFERENCES usp_categorie_article(id),
    CONSTRAINT fk_usp_article_marque FOREIGN KEY (marque_id) REFERENCES usp_marque(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_article_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    type_media VARCHAR(20) NOT NULL,
    url VARCHAR(1000),
    chemin_local VARCHAR(500),
    ordre_affichage INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_article_media_article (article_id),
    CONSTRAINT fk_usp_article_media FOREIGN KEY (article_id) REFERENCES usp_article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_mouvement_stock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    type_mouvement VARCHAR(30) NOT NULL,
    quantite_avant DECIMAL(15,3),
    quantite_mouvement DECIMAL(15,3),
    quantite_apres DECIMAL(15,3),
    source VARCHAR(50),
    reference_source VARCHAR(100),
    commentaire VARCHAR(500),
    utilisateur_id BIGINT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_mvt_stock_article (article_id),
    KEY idx_usp_mvt_stock_date (created_at),
    CONSTRAINT fk_usp_mvt_stock_article FOREIGN KEY (article_id) REFERENCES usp_article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_article_liste_attente (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    notifie BOOLEAN NOT NULL DEFAULT FALSE,
    date_notification DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_attente (article_id, contact_id),
    KEY idx_usp_attente_article (article_id),
    CONSTRAINT fk_usp_attente_article FOREIGN KEY (article_id) REFERENCES usp_article(id),
    CONSTRAINT fk_usp_attente_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Catégories d'exemple (section 12.2)
INSERT INTO usp_categorie_article (code, libelle, actif, created_at) VALUES
 ('MEDICAMENTS', 'Médicaments', TRUE, NOW()),
 ('COMPLEMENTS', 'Compléments alimentaires', TRUE, NOW()),
 ('BEBE', 'Produits bébé', TRUE, NOW()),
 ('DERMO', 'Dermocosmétique', TRUE, NOW()),
 ('HYGIENE', 'Hygiène', TRUE, NOW()),
 ('MATERIEL', 'Matériel médical', TRUE, NOW()),
 ('PARA', 'Parapharmacie', TRUE, NOW()),
 ('SOINS', 'Premiers soins', TRUE, NOW());
