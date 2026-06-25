-- =====================================================================
-- UbiSenderPro - Règles de programmation du risque de rupture (§11)
-- Jour du mois + heure + audience + canal, configurables.
-- Règles initiales : Diamond & Platinium le 1er, Diamond le 15.
-- =====================================================================

CREATE TABLE usp_dispo_regle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    libelle VARCHAR(150) NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'RISQUE_RUPTURE',
    jour_mois INT NOT NULL,
    heure INT NOT NULL DEFAULT 8,
    audience VARCHAR(40),
    canal VARCHAR(10) DEFAULT 'WEB',
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO usp_dispo_regle (libelle, type, jour_mois, heure, audience, canal, actif, created_at)
VALUES ('Risque - Diamond & Platinium (1er du mois)', 'RISQUE_RUPTURE', 1, 8, 'DIAMOND_ET_PLATINIUM', 'WEB', 1, NOW()),
       ('Risque - Diamond (15 du mois)', 'RISQUE_RUPTURE', 15, 8, 'DIAMOND', 'WEB', 1, NOW());
