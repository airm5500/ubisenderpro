-- =====================================================================
-- UBIPHARM CRM - Envoi WhatsApp des relances (service wa-web, Baileys)
-- Une session WhatsApp Web par numero professionnel d'agent. Les reponses
-- des clients restent dans le WhatsApp de l'agent ; le CRM en garde une
-- copie pour l'historique (cahier des charges 5).
-- =====================================================================

-- Etat des sessions remonte par le service (callback /status)
CREATE TABLE crm_session_whatsapp (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'DECONNECTE',
    sante VARCHAR(20) NOT NULL DEFAULT 'OK',
    raison VARCHAR(255),
    connecte_le DATETIME,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_session_whatsapp_numero (numero),
    CONSTRAINT ck_crm_session_whatsapp_statut CHECK (statut IN ('DECONNECTE', 'CONNEXION', 'QR', 'CONNECTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ENVOI = remis au service, en attente de sa reponse ; un message reste ENVOI apres
-- un arret brutal est passe en ECHEC (a verifier) plutot que renvoye en double.
ALTER TABLE crm_campagne_message
    ADD COLUMN tentatives INT NOT NULL DEFAULT 0 AFTER motif,
    DROP CONSTRAINT ck_crm_cm_statut,
    ADD CONSTRAINT ck_crm_cm_statut CHECK (statut IN ('EXCLU', 'A_ENVOYER', 'ENVOI', 'ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC', 'ANNULE')),
    ADD KEY idx_crm_cm_envoi (statut, numero_expediteur),
    ADD KEY idx_crm_cm_whatsapp (numero_expediteur, identifiant_whatsapp),
    ADD KEY idx_crm_cm_destinataire (numero_expediteur, numero_destinataire, envoye_le);

-- Reponses des clients (copie ; l'agent repond depuis son WhatsApp)
CREATE TABLE crm_message_recu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero_agent VARCHAR(30) NOT NULL,
    numero_client VARCHAR(30) NOT NULL,
    nom_affiche VARCHAR(255),
    client_id BIGINT,
    campagne_message_id BIGINT,
    type VARCHAR(20) NOT NULL,
    contenu TEXT,
    identifiant_whatsapp VARCHAR(100) NOT NULL,
    recu_le DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_message_recu (numero_agent, identifiant_whatsapp),
    KEY idx_crm_mr_client (client_id, recu_le),
    KEY idx_crm_mr_agent (numero_agent, recu_le),
    CONSTRAINT fk_crm_mr_client FOREIGN KEY (client_id) REFERENCES crm_client(id),
    CONSTRAINT fk_crm_mr_message FOREIGN KEY (campagne_message_id) REFERENCES crm_campagne_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Debit lent pour limiter le risque de blocage des numeros (canal non officiel)
INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('whatsapp.envoi.delai_min_secondes', '20',  'Attente minimale entre deux messages d''un meme numero', 'WHATSAPP', UTC_TIMESTAMP()),
 ('whatsapp.envoi.delai_max_secondes', '45',  'Attente maximale entre deux messages d''un meme numero', 'WHATSAPP', UTC_TIMESTAMP()),
 ('whatsapp.envoi.plafond_jour',       '200', 'Messages de relance par numero d''agent et par jour', 'WHATSAPP', UTC_TIMESTAMP()),
 ('whatsapp.envoi.heure_debut',        '8',   'Heure (Abidjan) a partir de laquelle les relances partent', 'WHATSAPP', UTC_TIMESTAMP()),
 ('whatsapp.envoi.heure_fin',          '18',  'Heure (Abidjan) apres laquelle plus aucune relance ne part', 'WHATSAPP', UTC_TIMESTAMP()),
 ('whatsapp.envoi.tentatives_max',     '3',   'Essais avant de passer un message en echec quand le service ne repond pas', 'WHATSAPP', UTC_TIMESTAMP());
