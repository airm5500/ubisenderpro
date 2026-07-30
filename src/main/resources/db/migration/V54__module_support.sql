-- =====================================================================
-- Module « Centre de Support » — V54 (additif, tables isolées usp_support_*)
--   1. Demandes « Me contacter » (e-mail support + archivage)
--   2. Tickets support (workflow type ServiceNow) + conversation
--   3. Journal d'événements applicatifs (capture auto, dédoublonnage)
--   4. Rôle système SUPPORT + utilisateur éditeur masqué (flag systeme)
--   5. Paramètres : e-mail support, rétention des événements
-- =====================================================================

-- 1. Demandes « Me contacter »
CREATE TABLE IF NOT EXISTS usp_support_demande (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150),
    societe VARCHAR(150),
    email VARCHAR(150),
    telephone VARCHAR(50),
    objet VARCHAR(255) NOT NULL,
    corps TEXT NOT NULL,
    pieces VARCHAR(500),
    statut VARCHAR(20) NOT NULL DEFAULT 'ENVOYEE',
    erreur VARCHAR(500),
    cree_par VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Tickets support
CREATE TABLE IF NOT EXISTS usp_support_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    client_id BIGINT,
    societe VARCHAR(150),
    utilisateur VARCHAR(100),
    module VARCHAR(50),
    type VARCHAR(30) NOT NULL DEFAULT 'INCIDENT',
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    sujet VARCHAR(255) NOT NULL,
    description TEXT,
    statut VARCHAR(30) NOT NULL DEFAULT 'NOUVEAU',
    affecte_a VARCHAR(100),
    event_signature VARCHAR(64),
    pieces VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_support_ticket_numero (numero),
    KEY idx_usp_ticket_statut (statut),
    KEY idx_usp_ticket_utilisateur (utilisateur)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Conversation d'un ticket
CREATE TABLE IF NOT EXISTS usp_support_ticket_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    direction VARCHAR(10) NOT NULL DEFAULT 'CLIENT',
    auteur VARCHAR(100),
    corps TEXT NOT NULL,
    pieces VARCHAR(500),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usp_tmsg_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Journal d'événements applicatifs (capture auto, dédup par signature)
CREATE TABLE IF NOT EXISTS usp_application_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    module VARCHAR(50),
    type VARCHAR(30) NOT NULL,
    niveau VARCHAR(10) NOT NULL DEFAULT 'ERROR',
    signature VARCHAR(64) NOT NULL,
    message_court VARCHAR(500),
    occurrences INT NOT NULL DEFAULT 1,
    utilisateur VARCHAR(100),
    url_ou_ecran VARCHAR(255),
    payload_json TEXT,
    ticket_id BIGINT,
    created_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_appevent_signature (signature),
    KEY idx_usp_appevent_niveau (niveau),
    KEY idx_usp_appevent_last (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Rôle système SUPPORT
INSERT INTO usp_role (code, libelle, description, actif, created_at)
SELECT 'SUPPORT', 'Support éditeur', 'Centre de support (rôle système)', TRUE, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_role WHERE code = 'SUPPORT');

-- Utilisateur éditeur masqué : flag systeme (exclu de la grille Utilisateurs).
ALTER TABLE usp_utilisateur ADD COLUMN systeme TINYINT(1) NOT NULL DEFAULT 0;

INSERT INTO usp_utilisateur (login, nom_complet, mot_de_passe_hash, actif, systeme, created_at)
SELECT 'support', 'Éditeur — Support', 'A_INITIALISER', TRUE, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_utilisateur WHERE login = 'support');

INSERT INTO usp_utilisateur_role (utilisateur_id, role_id)
SELECT u.id, r.id FROM usp_utilisateur u, usp_role r
WHERE u.login = 'support' AND r.code = 'SUPPORT'
  AND NOT EXISTS (SELECT 1 FROM usp_utilisateur_role ur WHERE ur.utilisateur_id = u.id AND ur.role_id = r.id);

-- 6. Paramètres du module
INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'support.email', '', 'Adresse e-mail du support éditeur (destinataire de « Me contacter »)', 'SUPPORT', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'support.email');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'support.retention_jours', '90', 'Rétention (jours) du journal d''événements du support', 'SUPPORT', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'support.retention_jours');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'support.mot_de_passe_initial', 'Support@2026', 'Mot de passe initial du compte support (haché au 1er démarrage puis ignoré)', 'SECURITE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'support.mot_de_passe_initial');
