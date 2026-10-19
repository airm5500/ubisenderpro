-- =====================================================================
-- UBIPHARM CRM - Discussions, envoi WhatsApp en masse, historiques.
-- crm_message_sortant : messages WhatsApp hors campagne, ecrits dans une
-- discussion (reponse a un client) ou partis d'un envoi en masse. Les
-- relances et promotions restent dans crm_campagne_message ; l'historique
-- complet des envois reunit les deux.
-- Rien n'est supprime : un envoi en masse s'annule, ses messages restent.
-- =====================================================================

CREATE TABLE crm_envoi_masse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    numero_expediteur VARCHAR(30) NOT NULL,
    contenu TEXT NOT NULL,
    -- EN_COURS : les messages partent au debit des relances ; TERMINE : plus rien n'attend ; ANNULE : arrete a la main
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    nb_destinataires INT NOT NULL DEFAULT 0,
    nb_exclus INT NOT NULL DEFAULT 0,
    cree_par BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    annule_par BIGINT,
    termine_le DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_crm_envoi_masse_statut (statut, numero_expediteur),
    CONSTRAINT ck_crm_envoi_masse_statut CHECK (statut IN ('EN_COURS', 'TERMINE', 'ANNULE')),
    CONSTRAINT fk_crm_envoi_masse_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id),
    CONSTRAINT fk_crm_envoi_masse_annule_par FOREIGN KEY (annule_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_message_sortant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    origine VARCHAR(20) NOT NULL,
    envoi_masse_id BIGINT,
    numero_expediteur VARCHAR(30) NOT NULL,
    numero_destinataire VARCHAR(30) NOT NULL,
    nom_destinataire VARCHAR(255),
    client_id BIGINT,
    contenu TEXT NOT NULL,
    statut VARCHAR(20) NOT NULL,
    motif VARCHAR(255),
    identifiant_whatsapp VARCHAR(100),
    tentatives INT NOT NULL DEFAULT 0,
    cree_par BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    envoye_le DATETIME,
    distribue_le DATETIME,
    lu_le DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_message_sortant_masse (envoi_masse_id, numero_destinataire),
    KEY idx_crm_ms_envoi (statut, numero_expediteur),
    KEY idx_crm_ms_whatsapp (numero_expediteur, identifiant_whatsapp),
    KEY idx_crm_ms_discussion (numero_expediteur, numero_destinataire, created_at),
    KEY idx_crm_ms_envoye (envoye_le),
    CONSTRAINT ck_crm_ms_origine CHECK (origine IN ('DISCUSSION', 'ENVOI_MASSE')),
    CONSTRAINT ck_crm_ms_statut CHECK (statut IN ('EXCLU', 'A_ENVOYER', 'ENVOI', 'ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC', 'ANNULE')),
    CONSTRAINT fk_crm_ms_envoi FOREIGN KEY (envoi_masse_id) REFERENCES crm_envoi_masse (id),
    CONSTRAINT fk_crm_ms_client FOREIGN KEY (client_id) REFERENCES crm_client (id),
    CONSTRAINT fk_crm_ms_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_crm_mr_discussion ON crm_message_recu (numero_agent, numero_client, recu_le);
CREATE INDEX idx_crm_jc_utilisateur ON crm_journal_connexion (utilisateur_id, created_at);

INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('discussions.reponse_active', 'OUI', 'OUI_NON', NULL, TRUE, 'Répondre aux clients depuis l''écran Discussions (sinon lecture seule : l''agent répond depuis son téléphone)', 'WHATSAPP', 20, UTC_TIMESTAMP()),
 ('whatsapp.envoi_masse.destinataires_max', '1000', 'ENTIER', NULL, TRUE, 'Destinataires au plus par envoi en masse', 'WHATSAPP', 21, UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre) VALUES
 ('DISCUSSIONS', 'Discussions', 'Lire les échanges WhatsApp avec les clients et y répondre depuis son numéro', 'Échanges', NULL, 215),
 ('HISTORIQUE_ENVOIS', 'Historique des envois', 'Consulter tous les messages envoyés (relances, promotions, discussions, envois en masse)', 'Échanges', NULL, 240),
 ('ENVOI_MASSE', 'Envoi WhatsApp en masse', 'Envoyer un même message à une liste de numéros depuis son numéro WhatsApp', 'Échanges', NULL, 245);

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT DISTINCT rd.role_id, d.id, UTC_TIMESTAMP() FROM crm_role_droit rd
JOIN crm_droit a ON a.id = rd.droit_id AND a.code IN ('REPONSES', 'NUMERO_MARKETING')
JOIN crm_droit d ON d.code IN ('DISCUSSIONS', 'HISTORIQUE_ENVOIS');

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'ENVOI_MASSE'
WHERE r.code IN ('ADMIN', 'ADMINISTRATEUR_SANS_SMS', 'CHARGE_MARKETING');
