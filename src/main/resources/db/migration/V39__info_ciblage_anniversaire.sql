-- =====================================================================
-- UbiSenderPro - Informations : ciblage agence/région + dédup anniversaire
-- =====================================================================

-- Région ciblée par une information (l'agence existe déjà).
ALTER TABLE usp_info_evenement ADD COLUMN region VARCHAR(150) AFTER agence;

-- Valeurs de ciblage portées par la campagne (audience AGENCE / REGION).
ALTER TABLE usp_campagne
    ADD COLUMN agence_cible VARCHAR(150) AFTER segmentation_ids,
    ADD COLUMN region_cible VARCHAR(150) AFTER agence_cible;

-- Traçabilité des vœux d'anniversaire : un seul par contact et par année.
CREATE TABLE usp_anniversaire_envoi (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    annee INT NOT NULL,
    date_envoi DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anniv_contact_annee (contact_id, annee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
