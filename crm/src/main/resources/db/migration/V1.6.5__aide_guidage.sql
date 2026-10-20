-- =====================================================================
-- UBIPHARM CRM - Aide et guidage (demande d'Airman du 20/10/2026, maquette : 1B, 2B, 3C)
-- Infobulles detaillees sur les boutons, panneau d'aide « ? » de chaque page, retours animes pendant la saisie.
-- Les textes d'origine sont livres avec l'ecran ; seuls ceux reecrits par l'administrateur sont en base.
-- =====================================================================

CREATE TABLE crm_aide_texte (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- BOUTON : infobulle d'un bouton (cle « ecran|libelle » ou « *|libelle ») ; PAGE : panneau d'aide d'un ecran (cle = chemin)
    type_aide VARCHAR(10) NOT NULL,
    cle VARCHAR(200) NOT NULL,
    titre VARCHAR(150) NOT NULL,
    texte TEXT NOT NULL,
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_aide_texte (type_aide, cle),
    CONSTRAINT fk_crm_aide_texte_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_aide_texte_type CHECK (type_aide IN ('BOUTON', 'PAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Choix personnel : un agent experimente peut couper le guidage pour lui seul
ALTER TABLE crm_utilisateur ADD COLUMN guidage_actif BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('aide.guidage', 'OUI', 'OUI_NON', NULL, NULL, TRUE,
  'Guidage pour toute l''entreprise : infobulles détaillées des boutons et retours animés pendant la saisie (chaque utilisateur peut aussi le couper pour lui)',
  'AIDE', 1, UTC_TIMESTAMP());
