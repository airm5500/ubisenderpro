-- =====================================================================
-- UbiSenderPro - Module MARKETING (P2) : calendrier & propositions d'envoi
-- Propositions générées automatiquement à partir des promotions.
-- Validation humaine obligatoire avant transformation en campagne.
-- =====================================================================

CREATE TABLE usp_envoi_propose (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- Clé d'idempotence : évite de regénérer deux fois la même proposition.
    cle VARCHAR(120) NOT NULL,
    -- ANNONCE_MENSUELLE | LANCEMENT | RAPPEL_J7 | RAPPEL_J3 | RAPPEL_J1
    type VARCHAR(30) NOT NULL,
    -- null pour l'annonce mensuelle (regroupe plusieurs promotions).
    promotion_id BIGINT,
    titre VARCHAR(200) NOT NULL,
    message TEXT,
    date_prevue DATE NOT NULL,
    -- PROPOSEE | VALIDEE | REJETEE | EXPIREE
    statut VARCHAR(20) NOT NULL DEFAULT 'PROPOSEE',
    -- Renseigné à la validation (la proposition devient cette campagne).
    campagne_id BIGINT,
    -- Audience : vide par défaut, choisie au moment de la validation.
    liste_id BIGINT,
    segment_id BIGINT,
    motif_rejet VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usp_envoi_cle (cle),
    KEY idx_usp_envoi_statut (statut),
    KEY idx_usp_envoi_date (date_prevue),
    KEY idx_usp_envoi_promo (promotion_id),
    CONSTRAINT fk_usp_envoi_promo FOREIGN KEY (promotion_id)
        REFERENCES usp_promotion(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
