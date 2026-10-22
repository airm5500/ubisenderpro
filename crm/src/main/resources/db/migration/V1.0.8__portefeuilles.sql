-- =====================================================================
-- UBIPHARM CRM - Portefeuilles des agents de recouvrement
-- Cadrage du 16/10/2026 : un client = un agent (pas de co-gestion),
-- affectation saisie dans la plateforme, historique conserve ; interim
-- autorise par la Direction du Recouvrement, le remplacant envoie depuis
-- le numero de l'agent absent pendant une periode datee.
-- =====================================================================

-- Numero WhatsApp professionnel de l'agent (flotte de la societe), unique
ALTER TABLE crm_utilisateur ADD UNIQUE KEY uk_crm_utilisateur_numero (numero_whatsapp);

CREATE TABLE crm_affectation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    date_debut DATETIME NOT NULL,
    date_fin DATETIME,
    motif_debut VARCHAR(255),
    motif_fin VARCHAR(255),
    affecte_par BIGINT NOT NULL,
    retire_par BIGINT,
    -- Garantit une seule affectation en cours par client
    client_en_cours BIGINT AS (CASE WHEN date_fin IS NULL THEN client_id END) PERSISTENT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_affectation_en_cours (client_en_cours),
    KEY idx_crm_affectation_client (client_id, date_debut),
    KEY idx_crm_affectation_agent (agent_id, date_fin),
    CONSTRAINT fk_crm_affectation_client FOREIGN KEY (client_id) REFERENCES crm_client(id),
    CONSTRAINT fk_crm_affectation_agent FOREIGN KEY (agent_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_affectation_par FOREIGN KEY (affecte_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_affectation_retire FOREIGN KEY (retire_par) REFERENCES crm_utilisateur(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_delegation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    titulaire_id BIGINT NOT NULL,
    remplacant_id BIGINT NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    motif VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    autorise_par BIGINT NOT NULL,
    annule_par BIGINT,
    annule_le DATETIME,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_delegation_remplacant (remplacant_id, statut, date_debut, date_fin),
    KEY idx_crm_delegation_titulaire (titulaire_id, statut, date_debut, date_fin),
    CONSTRAINT fk_crm_delegation_titulaire FOREIGN KEY (titulaire_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_delegation_remplacant FOREIGN KEY (remplacant_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_delegation_autorise FOREIGN KEY (autorise_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_delegation_annule FOREIGN KEY (annule_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_delegation_statut CHECK (statut IN ('ACTIVE', 'ANNULEE')),
    CONSTRAINT ck_crm_delegation_dates CHECK (date_fin >= date_debut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
