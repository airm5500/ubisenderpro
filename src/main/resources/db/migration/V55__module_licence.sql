-- =====================================================================
-- Gestion des licences — V55 (additif)
--   État de licence courant (le catalogue vit côté UbiLicense Manager)
--   + audit local des événements de licence + paramètre d'activation.
-- =====================================================================

CREATE TABLE IF NOT EXISTS usp_licence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id VARCHAR(60),
    societe VARCHAR(150),
    pays VARCHAR(100),
    email VARCHAR(150),
    type VARCHAR(30),
    date_activation DATE,
    date_expiration DATE,
    max_users INT,
    max_agences INT,
    modules VARCHAR(1000),
    version_min VARCHAR(20),
    version_max VARCHAR(20),
    empreinte_serveur VARCHAR(120),
    signature TEXT,
    payload TEXT,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    derniere_date_vue DATETIME,
    importee_le DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usp_licence_evenement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(30) NOT NULL,
    detail VARCHAR(500),
    utilisateur VARCHAR(100),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- licence.obligatoire = false : l'application fonctionne sans licence (bandeau
-- d'information seulement). L'éditeur la passe à true pour un déploiement
-- commercial — les restrictions s'appliquent alors (modules, expiration).
INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'licence.obligatoire', 'false',
       'true = licence exigée (modules filtrés, envois bloqués après expiration + grâce)', 'LICENCE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'licence.obligatoire');

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'licence.grace_jours', '7',
       'Période de grâce (jours) après expiration avant blocage des envois', 'LICENCE', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'licence.grace_jours');
