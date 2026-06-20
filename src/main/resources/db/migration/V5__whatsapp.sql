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
    whatsapp_account_id BIGINT NOT NULL,
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
