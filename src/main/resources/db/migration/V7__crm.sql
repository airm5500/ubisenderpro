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

-- Automatisations d'exemple (section 22)
INSERT INTO usp_automatisation (nom, declencheur, mot_cle, action, actif, created_at) VALUES
 ('Message d''accueil', 'PREMIER_MESSAGE', NULL, 'ENVOYER_ACCUEIL', TRUE, NOW()),
 ('Catalogue', 'MOT_CLE', 'Catalogue', 'ENVOYER_CATALOGUE', TRUE, NOW()),
 ('Désabonnement STOP', 'MOT_CLE', 'STOP', 'DESABONNER', TRUE, NOW());
