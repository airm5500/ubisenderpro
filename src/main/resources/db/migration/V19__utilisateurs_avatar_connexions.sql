-- =====================================================================
-- UbiSenderPro - V19 : avatar utilisateur + historique des connexions
-- =====================================================================

ALTER TABLE usp_utilisateur
    ADD COLUMN avatar VARCHAR(16) NULL AFTER nom_complet;

CREATE TABLE usp_connexion_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT,
    login VARCHAR(100),
    session_token VARCHAR(120),
    ip VARCHAR(60),
    poste VARCHAR(255),
    lieu VARCHAR(255),
    connexion_at DATETIME NOT NULL,
    deconnexion_at DATETIME,
    duree_secondes BIGINT,
    PRIMARY KEY (id),
    KEY idx_usp_conlog_user (utilisateur_id),
    KEY idx_usp_conlog_date (connexion_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
