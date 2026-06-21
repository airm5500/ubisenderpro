-- =====================================================================
-- UbiSenderPro - V15 : réchauffeur (warming) des sessions WhatsApp Web
-- =====================================================================

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
