-- =====================================================================
-- UbiSenderPro - Script d'installation complet (hors Flyway)
-- Base : ubisenderpro_db (MariaDB, utf8mb4)
--
-- Usage :
--   1) CREATE DATABASE ubisenderpro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   2) mysql -u root ubisenderpro_db < ubisenderpro_install.sql
--   3) Démarrer l'application avec Flyway désactivé :
--        export UBISENDERPRO_SKIP_FLYWAY=true   (ou -Dubisenderpro.skipFlyway=true)
--
-- Compte admin par défaut : login = admin / mot de passe = Admin@2026
-- (hash BCrypt déjà inséré ci-dessous ; à changer après la première connexion)
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;


-- ===== V1__socle =====
-- =====================================================================
-- UbiSenderPro - Phase 1 : Socle technique
-- Tables : paramètres, utilisateurs, rôles, permissions, journal
-- =====================================================================

CREATE TABLE usp_parametre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cle VARCHAR(100) NOT NULL,
    valeur VARCHAR(2000),
    description VARCHAR(500),
    categorie VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_parametre_cle (cle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    libelle VARCHAR(150) NOT NULL,
    module VARCHAR(80),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_utilisateur (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login VARCHAR(100) NOT NULL,
    nom_complet VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    mot_de_passe_hash VARCHAR(255) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_connexion DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_utilisateur_login (login),
    KEY idx_usp_utilisateur_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_utilisateur_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_utilisateur_role (utilisateur_id, role_id),
    CONSTRAINT fk_usp_ur_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES usp_utilisateur(id),
    CONSTRAINT fk_usp_ur_role FOREIGN KEY (role_id) REFERENCES usp_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_role_permission (role_id, permission_id),
    CONSTRAINT fk_usp_rp_role FOREIGN KEY (role_id) REFERENCES usp_role(id),
    CONSTRAINT fk_usp_rp_permission FOREIGN KEY (permission_id) REFERENCES usp_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_journal_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT,
    login VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    entite VARCHAR(100),
    entite_id BIGINT,
    details TEXT,
    adresse_ip VARCHAR(60),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_journal_utilisateur (utilisateur_id),
    KEY idx_usp_journal_action (action),
    KEY idx_usp_journal_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----- Données de référence : rôles (section 26.2 de la spec) -----
INSERT INTO usp_role (code, libelle, description, actif, created_at) VALUES
 ('ADMIN',        'Administrateur',        'Accès complet',                     TRUE, NOW()),
 ('MARKETING',    'Responsable marketing', 'Clients, segments, campagnes',      TRUE, NOW()),
 ('SUPERVISEUR',  'Superviseur',           'Conversations, affectations',       TRUE, NOW()),
 ('AGENT',        'Agent',                 'Conversations affectées',           TRUE, NOW()),
 ('CATALOGUE',    'Gestionnaire catalogue','Articles, catégories, marques',     TRUE, NOW()),
 ('LECTURE',      'Lecture seule',         'Consultation autorisée',            TRUE, NOW());

-- ----- Compte administrateur par défaut -----
-- Login : admin   /   Mot de passe initial : voir paramètre 'admin.mot_de_passe_initial'.
-- Le hash BCrypt est calculé par l'application au premier démarrage (voir Bootstrap.java),
-- car le hash ne peut pas être généré de façon fiable en SQL pur.
INSERT INTO usp_utilisateur (login, nom_complet, email, mot_de_passe_hash, actif, created_at) VALUES
 ('admin', 'Administrateur', 'admin@ubisenderpro.local', '$2a$10$g9vg5p5dcr2KCLmdi1dfuu1TW7gY1Xxy7N/Icq0AkyiZuGSwan2rO', TRUE, NOW());

INSERT INTO usp_utilisateur_role (utilisateur_id, role_id)
SELECT u.id, r.id FROM usp_utilisateur u, usp_role r WHERE u.login = 'admin' AND r.code = 'ADMIN';

-- ----- Paramètres par défaut -----
INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('app.nom',             'UbiSenderPro',  'Nom de l''application',            'GENERAL', NOW()),
 ('app.societe',         'UbiSenderPro',  'Société émettrice (variable [SOCIETE])','GENERAL', NOW()),
 ('whatsapp.prefixe_pays','225',          'Préfixe pays par défaut (CI)',     'WHATSAPP', NOW()),
 ('import.taille_max_mo','10',            'Taille maximale d''un import (Mo)','IMPORT', NOW()),
 ('session.expiration_min','60',          'Expiration de session (minutes)',  'SECURITE', NOW()),
 ('whatsapp.mode_envoi',  'API',           'Mode d''envoi par défaut : API (Cloud officielle) ou WEB (WhatsApp Web)','WHATSAPP', NOW()),
 ('admin.mot_de_passe_initial','Admin@2026','Mot de passe initial du compte admin (haché au 1er démarrage puis ignoré)','SECURITE', NOW());

-- ===== V2__clients =====
-- =====================================================================
-- UbiSenderPro - Phase 1 : Référentiel clients
-- Tables : segmentation, comptes clients, contacts, tags
-- =====================================================================

CREATE TABLE usp_segmentation_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    ordre_affichage INT NOT NULL DEFAULT 0,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_segmentation_code (code),
    UNIQUE KEY uk_usp_segmentation_libelle (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero_client VARCHAR(50) NOT NULL,
    nom_compte VARCHAR(255) NOT NULL,
    agence VARCHAR(100),
    region VARCHAR(150),
    email_principal VARCHAR(150),
    segmentation_id BIGINT,
    adresse VARCHAR(500),
    ville VARCHAR(100),
    commune VARCHAR(100),
    pays VARCHAR(100),
    statut VARCHAR(30) NOT NULL DEFAULT 'ACTIF',
    notes TEXT,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_client_numero (numero_client),
    KEY idx_usp_client_nom_compte (nom_compte),
    KEY idx_usp_client_agence (agence),
    KEY idx_usp_client_region (region),
    KEY idx_usp_client_segmentation (segmentation_id),
    CONSTRAINT fk_usp_client_segmentation FOREIGN KEY (segmentation_id) REFERENCES usp_segmentation_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    nom_complet VARCHAR(255) NOT NULL,
    fonction VARCHAR(150),
    telephone_principal VARCHAR(25),
    telephone_2 VARCHAR(25),
    numero_whatsapp VARCHAR(25),
    email VARCHAR(150),
    contact_principal BOOLEAN NOT NULL DEFAULT FALSE,
    consentement_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    date_consentement DATETIME,
    source_consentement VARCHAR(150),
    desabonne BOOLEAN NOT NULL DEFAULT FALSE,
    bloque BOOLEAN NOT NULL DEFAULT FALSE,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_interaction DATETIME,
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_contact_client (client_id),
    KEY idx_usp_contact_telephone (telephone_principal),
    KEY idx_usp_contact_whatsapp (numero_whatsapp),
    KEY idx_usp_contact_email (email),
    CONSTRAINT fk_usp_contact_client FOREIGN KEY (client_id) REFERENCES usp_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(100) NOT NULL,
    couleur VARCHAR(20),
    description VARCHAR(300),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_tag_libelle (libelle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_client_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_client_tag (client_id, tag_id),
    CONSTRAINT fk_usp_ct_client FOREIGN KEY (client_id) REFERENCES usp_client(id),
    CONSTRAINT fk_usp_ct_tag FOREIGN KEY (tag_id) REFERENCES usp_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_contact_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_contact_tag (contact_id, tag_id),
    CONSTRAINT fk_usp_cot_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id),
    CONSTRAINT fk_usp_cot_tag FOREIGN KEY (tag_id) REFERENCES usp_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----- Segmentations de référence (section 9.2 de la spec) -----
INSERT INTO usp_segmentation_client (code, libelle, description, ordre_affichage, actif, created_at) VALUES
 ('PLATINIUM', 'Platinium', NULL, 1, TRUE, NOW()),
 ('GOLD',      'Gold',      NULL, 2, TRUE, NOW()),
 ('SILVER',    'Silver',    NULL, 3, TRUE, NOW()),
 ('STANDARD',  'Standard',  NULL, 4, TRUE, NOW()),
 ('PROSPECT',  'Prospect',  NULL, 5, TRUE, NOW());

-- ===== V3__import =====
-- =====================================================================
-- UbiSenderPro - Phase 1 : Journal des imports (section 25.6 de la spec)
-- =====================================================================

CREATE TABLE usp_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom_fichier VARCHAR(255),
    type_import VARCHAR(50) NOT NULL,
    utilisateur_id BIGINT,
    mode_import VARCHAR(50),
    nb_lignes INT NOT NULL DEFAULT 0,
    nb_crees INT NOT NULL DEFAULT 0,
    nb_mis_a_jour INT NOT NULL DEFAULT 0,
    nb_ignores INT NOT NULL DEFAULT 0,
    nb_rejetes INT NOT NULL DEFAULT 0,
    duree_ms BIGINT,
    statut VARCHAR(30) NOT NULL DEFAULT 'EN_COURS',
    fichier_erreurs LONGTEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_import_type (type_import),
    KEY idx_usp_import_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_import_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_id BIGINT NOT NULL,
    numero_ligne INT NOT NULL,
    statut VARCHAR(30) NOT NULL,
    message VARCHAR(1000),
    contenu_ligne TEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_import_detail_import (import_id),
    CONSTRAINT fk_usp_import_detail FOREIGN KEY (import_id) REFERENCES usp_import(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_import_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    type_import VARCHAR(50) NOT NULL,
    mapping_json TEXT NOT NULL,
    separateur VARCHAR(5),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_import_mapping_nom (nom, type_import)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== V4__catalogue =====
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

-- ===== V5__whatsapp =====
-- =====================================================================
-- UbiSenderPro - Phase 2 : WhatsApp, conversations, messages, modèles
-- =====================================================================

CREATE TABLE usp_whatsapp_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(150) NOT NULL,
    phone_number_id VARCHAR(100) NOT NULL,
    business_account_id VARCHAR(100),
    numero_affiche VARCHAR(30),
    access_token TEXT,
    verify_token VARCHAR(150),
    api_version VARCHAR(20) NOT NULL DEFAULT 'v19.0',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    mode_test BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_wa_phone (phone_number_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_modele_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    type_modele VARCHAR(40) NOT NULL,
    langue VARCHAR(10) NOT NULL DEFAULT 'fr',
    categorie VARCHAR(40),
    entete_texte VARCHAR(255),
    entete_media_type VARCHAR(20),
    entete_media_url VARCHAR(1000),
    corps TEXT NOT NULL,
    pied_de_page VARCHAR(255),
    boutons_json TEXT,
    nom_modele_whatsapp VARCHAR(150),
    statut_approbation VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_modele_nom (nom, langue),
    KEY idx_usp_modele_type (type_modele)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    canal VARCHAR(10) NOT NULL DEFAULT 'API',
    whatsapp_account_id BIGINT,
    wa_web_session_id BIGINT,
    contact_id BIGINT,
    client_id BIGINT,
    numero_whatsapp VARCHAR(25) NOT NULL,
    nom_affiche VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'OUVERTE',
    agent_id BIGINT,
    non_lu INT NOT NULL DEFAULT 0,
    dernier_message VARCHAR(1000),
    date_dernier_message DATETIME,
    fenetre_expire_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_conv_account (whatsapp_account_id),
    KEY idx_usp_conv_contact (contact_id),
    KEY idx_usp_conv_numero (numero_whatsapp),
    KEY idx_usp_conv_statut (statut),
    KEY idx_usp_conv_agent (agent_id),
    CONSTRAINT fk_usp_conv_account FOREIGN KEY (whatsapp_account_id) REFERENCES usp_whatsapp_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_conversation_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    affecte_par BIGINT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_assign_conv (conversation_id),
    CONSTRAINT fk_usp_assign_conv FOREIGN KEY (conversation_id) REFERENCES usp_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_conversation_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_conv_tag (conversation_id, tag_id),
    CONSTRAINT fk_usp_convtag_conv FOREIGN KEY (conversation_id) REFERENCES usp_conversation(id),
    CONSTRAINT fk_usp_convtag_tag FOREIGN KEY (tag_id) REFERENCES usp_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    wa_message_id VARCHAR(150),
    direction VARCHAR(10) NOT NULL,
    type_message VARCHAR(20) NOT NULL DEFAULT 'TEXTE',
    contenu TEXT,
    modele_id BIGINT,
    statut VARCHAR(20) NOT NULL DEFAULT 'ENVOYE',
    note_interne BOOLEAN NOT NULL DEFAULT FALSE,
    erreur VARCHAR(500),
    expediteur_id BIGINT,
    created_at DATETIME NOT NULL,
    delivered_at DATETIME,
    read_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_message_conv (conversation_id),
    KEY idx_usp_message_wa (wa_message_id),
    KEY idx_usp_message_statut (statut),
    CONSTRAINT fk_usp_message_conv FOREIGN KEY (conversation_id) REFERENCES usp_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_message_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    type_media VARCHAR(20) NOT NULL,
    wa_media_id VARCHAR(150),
    url VARCHAR(1000),
    chemin_local VARCHAR(500),
    mime_type VARCHAR(100),
    nom_fichier VARCHAR(255),
    taille BIGINT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_message_media (message_id),
    CONSTRAINT fk_usp_message_media FOREIGN KEY (message_id) REFERENCES usp_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_media_fichier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom_fichier VARCHAR(255),
    mime_type VARCHAR(100),
    taille BIGINT,
    contenu LONGBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_webhook_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(40) NOT NULL DEFAULT 'WHATSAPP',
    type_event VARCHAR(60),
    payload LONGTEXT,
    traite BOOLEAN NOT NULL DEFAULT FALSE,
    erreur VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_webhook_traite (traite),
    KEY idx_usp_webhook_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== V6__campagnes =====
-- =====================================================================
-- UbiSenderPro - Phase 2 : Listes, segments, campagnes
-- =====================================================================

CREATE TABLE usp_liste_diffusion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_liste_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_liste_diffusion_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    liste_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    source VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_liste_contact (liste_id, contact_id),
    KEY idx_usp_liste_contact_liste (liste_id),
    CONSTRAINT fk_usp_lc_liste FOREIGN KEY (liste_id) REFERENCES usp_liste_diffusion(id),
    CONSTRAINT fk_usp_lc_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    logique VARCHAR(5) NOT NULL DEFAULT 'ET',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_segment_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_segment_filtre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    segment_id BIGINT NOT NULL,
    critere VARCHAR(60) NOT NULL,
    operateur VARCHAR(20) NOT NULL DEFAULT 'EGAL',
    valeur VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_segment_filtre (segment_id),
    CONSTRAINT fk_usp_segment_filtre FOREIGN KEY (segment_id) REFERENCES usp_segment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_campagne (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    objectif VARCHAR(255),
    categorie VARCHAR(60),
    responsable_id BIGINT,
    whatsapp_account_id BIGINT,
    modele_id BIGINT,
    liste_id BIGINT,
    segment_id BIGINT,
    segmentation_id BIGINT,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    date_programmee DATETIME,
    fuseau_horaire VARCHAR(60),
    nb_destinataires INT NOT NULL DEFAULT 0,
    nb_envoyes INT NOT NULL DEFAULT 0,
    nb_distribues INT NOT NULL DEFAULT 0,
    nb_lus INT NOT NULL DEFAULT 0,
    nb_repondus INT NOT NULL DEFAULT 0,
    nb_echoues INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_campagne_statut (statut),
    KEY idx_usp_campagne_date (date_programmee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_campagne_destinataire (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campagne_id BIGINT NOT NULL,
    contact_id BIGINT,
    numero_whatsapp VARCHAR(25),
    nom_contact VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    wa_message_id VARCHAR(150),
    erreur VARCHAR(500),
    envoye_at DATETIME,
    distribue_at DATETIME,
    lu_at DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_camp_dest_campagne (campagne_id),
    KEY idx_usp_camp_dest_statut (statut),
    CONSTRAINT fk_usp_camp_dest FOREIGN KEY (campagne_id) REFERENCES usp_campagne(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== V7__crm =====
-- =====================================================================
-- UbiSenderPro - Phase 2 : CRM commercial, automatisations, consentements
-- =====================================================================

CREATE TABLE usp_opportunite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT,
    contact_id BIGINT,
    agent_id BIGINT,
    conversation_id BIGINT,
    campagne_id BIGINT,
    origine VARCHAR(60),
    montant_estime DECIMAL(15,2),
    probabilite INT,
    prochaine_action VARCHAR(255),
    date_relance DATETIME,
    statut VARCHAR(30) NOT NULL DEFAULT 'NOUVEAU_CONTACT',
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_opp_client (client_id),
    KEY idx_usp_opp_statut (statut),
    KEY idx_usp_opp_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_opportunite_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opportunite_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    quantite DECIMAL(15,3) NOT NULL DEFAULT 1,
    prix_estime DECIMAL(15,2),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_opp_article (opportunite_id),
    CONSTRAINT fk_usp_opp_article_opp FOREIGN KEY (opportunite_id) REFERENCES usp_opportunite(id),
    CONSTRAINT fk_usp_opp_article_art FOREIGN KEY (article_id) REFERENCES usp_article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_tache (
    id BIGINT NOT NULL AUTO_INCREMENT,
    titre VARCHAR(255) NOT NULL,
    description TEXT,
    client_id BIGINT,
    contact_id BIGINT,
    opportunite_id BIGINT,
    conversation_id BIGINT,
    assigne_id BIGINT,
    statut VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE',
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    echeance DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_tache_assigne (assigne_id),
    KEY idx_usp_tache_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_commande (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero_commande VARCHAR(50) NOT NULL,
    client_id BIGINT NOT NULL,
    contact_id BIGINT,
    conversation_id BIGINT,
    campagne_id BIGINT,
    utilisateur_id BIGINT,
    statut VARCHAR(40) NOT NULL,
    date_commande DATETIME NOT NULL,
    montant_brut DECIMAL(15,2) NOT NULL DEFAULT 0,
    montant_remise DECIMAL(15,2) NOT NULL DEFAULT 0,
    montant_total DECIMAL(15,2) NOT NULL DEFAULT 0,
    mode_retrait VARCHAR(50),
    adresse_livraison VARCHAR(500),
    mode_paiement VARCHAR(50),
    notes TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_commande_numero (numero_commande),
    KEY idx_usp_commande_client (client_id),
    KEY idx_usp_commande_date (date_commande),
    KEY idx_usp_commande_statut (statut),
    CONSTRAINT fk_usp_commande_client FOREIGN KEY (client_id) REFERENCES usp_client(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_commande_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    commande_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    designation VARCHAR(255) NOT NULL,
    quantite DECIMAL(15,3) NOT NULL,
    prix_unitaire DECIMAL(15,2) NOT NULL,
    remise DECIMAL(15,2) NOT NULL DEFAULT 0,
    montant_total DECIMAL(15,2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_cmd_detail_commande (commande_id),
    KEY idx_usp_cmd_detail_article (article_id),
    CONSTRAINT fk_usp_cmd_detail_cmd FOREIGN KEY (commande_id) REFERENCES usp_commande(id),
    CONSTRAINT fk_usp_cmd_detail_art FOREIGN KEY (article_id) REFERENCES usp_article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_automatisation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    declencheur VARCHAR(60) NOT NULL,
    mot_cle VARCHAR(150),
    condition_json TEXT,
    action VARCHAR(60) NOT NULL,
    action_params_json TEXT,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_automat_declencheur (declencheur)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_consentement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    canal VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    accorde BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(150),
    date_consentement DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_consentement_contact (contact_id),
    CONSTRAINT fk_usp_consentement_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_desabonnement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    canal VARCHAR(30) NOT NULL DEFAULT 'WHATSAPP',
    motif VARCHAR(255),
    source VARCHAR(150),
    date_desabonnement DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_desab_contact (contact_id),
    CONSTRAINT fk_usp_desab_contact FOREIGN KEY (contact_id) REFERENCES usp_client_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Canal WhatsApp Web (non officiel) + envoi en masse
CREATE TABLE usp_wa_web_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(150) NOT NULL,
    numero VARCHAR(30),
    statut VARCHAR(20) NOT NULL DEFAULT 'DECONNECTE',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_wa_bulk_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    nom VARCHAR(150),
    msg1 TEXT, msg2 TEXT, msg3 TEXT, msg4 TEXT, msg5 TEXT,
    media_url VARCHAR(1000),
    media_type VARCHAR(20),
    media_mime VARCHAR(100),
    media_nom VARCHAR(255),
    attente_min INT NOT NULL DEFAULT 4,
    attente_max INT NOT NULL DEFAULT 8,
    pause_apres INT NOT NULL DEFAULT 10,
    pause_min INT NOT NULL DEFAULT 10,
    pause_max INT NOT NULL DEFAULT 20,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    date_programmee DATETIME,
    heure_debut INT NOT NULL DEFAULT 0,
    heure_fin INT NOT NULL DEFAULT 0,
    total INT NOT NULL DEFAULT 0,
    envoyes INT NOT NULL DEFAULT 0,
    echoues INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_bulk_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_wa_bulk_destinataire (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    numero VARCHAR(30) NOT NULL,
    nom VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    erreur VARCHAR(500),
    sent_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_bulk_dest_job (job_id),
    CONSTRAINT fk_usp_bulk_dest_job FOREIGN KEY (job_id) REFERENCES usp_wa_bulk_job(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usp_wa_warmup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT FALSE,
    numeros TEXT,
    par_jour_base INT NOT NULL DEFAULT 10,
    par_jour_max INT NOT NULL DEFAULT 60,
    increment_jour INT NOT NULL DEFAULT 10,
    jour_courant INT NOT NULL DEFAULT 1,
    envoyes_jour INT NOT NULL DEFAULT 0,
    date_jour DATE,
    dernier_envoi DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_warmup_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Automatisations d'exemple (section 22)
INSERT INTO usp_automatisation (nom, declencheur, mot_cle, action, actif, created_at) VALUES
 ('Message d''accueil', 'PREMIER_MESSAGE', NULL, 'ENVOYER_ACCUEIL', TRUE, NOW()),
 ('Catalogue', 'MOT_CLE', 'Catalogue', 'ENVOYER_CATALOGUE', TRUE, NOW()),
 ('Désabonnement STOP', 'MOT_CLE', 'STOP', 'DESABONNER', TRUE, NOW());

SET FOREIGN_KEY_CHECKS = 1;
