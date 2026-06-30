-- =====================================================================
-- UbiSenderPro - Module RECOUVREMENT (Lot 3 : assistant de relance)
-- Propositions générées automatiquement (jamais de relance à l'aveugle),
-- validées par l'utilisateur avant envoi.
-- =====================================================================

CREATE TABLE IF NOT EXISTS usp_rec_proposition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    -- RELANCE_PREVENTIVE | FACTURE_ECHUE | DEUXIEME_RELANCE | PROMESSE_NON_TENUE
    -- | PAIEMENT_PARTIEL | CLIENT_CRITIQUE
    motif VARCHAR(40) NOT NULL,
    -- NORMALE | HAUTE | CRITIQUE
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    jours_retard INT,
    montant DECIMAL(15,2),
    canal_recommande VARCHAR(20),
    modele_id BIGINT,
    -- PROPOSEE | VALIDEE | REJETEE
    statut VARCHAR(20) NOT NULL DEFAULT 'PROPOSEE',
    cle VARCHAR(120),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_usp_rec_prop_statut (statut),
    KEY idx_usp_rec_prop_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
