-- =====================================================================
-- UBIPHARM CRM - Referentiels de localisation des clients et badges des segments
-- Region, ville, commune et tournee ne sont plus saisies librement : le client
-- et l'import choisissent dans des listes tenues dans les Parametres.
-- Une ville appartient (ou non) a une region, une commune a une ville,
-- une tournee a une agence. Rien ne se supprime : on archive.
-- =====================================================================

CREATE TABLE crm_referentiel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type_referentiel VARCHAR(20) NOT NULL,
    code VARCHAR(30) NULL,
    libelle VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    agence_id BIGINT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_referentiel_type (type_referentiel, statut, libelle),
    CONSTRAINT fk_crm_referentiel_parent FOREIGN KEY (parent_id) REFERENCES crm_referentiel(id),
    CONSTRAINT fk_crm_referentiel_agence FOREIGN KEY (agence_id) REFERENCES crm_agence(id),
    CONSTRAINT fk_crm_referentiel_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_referentiel_type CHECK (type_referentiel IN ('REGION', 'VILLE', 'COMMUNE', 'TOURNEE')),
    CONSTRAINT ck_crm_referentiel_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Regions de Cote d'Ivoire (31 regions et les 2 districts autonomes)
INSERT INTO crm_referentiel (type_referentiel, libelle, created_at) VALUES
 ('REGION', 'District autonome d''Abidjan', UTC_TIMESTAMP()), ('REGION', 'District autonome de Yamoussoukro', UTC_TIMESTAMP()),
 ('REGION', 'Agnéby-Tiassa', UTC_TIMESTAMP()), ('REGION', 'Bafing', UTC_TIMESTAMP()), ('REGION', 'Bagoué', UTC_TIMESTAMP()),
 ('REGION', 'Bélier', UTC_TIMESTAMP()), ('REGION', 'Béré', UTC_TIMESTAMP()), ('REGION', 'Bounkani', UTC_TIMESTAMP()),
 ('REGION', 'Cavally', UTC_TIMESTAMP()), ('REGION', 'Folon', UTC_TIMESTAMP()), ('REGION', 'Gbêkê', UTC_TIMESTAMP()),
 ('REGION', 'Gbôklé', UTC_TIMESTAMP()), ('REGION', 'Gôh', UTC_TIMESTAMP()), ('REGION', 'Gontougo', UTC_TIMESTAMP()),
 ('REGION', 'Grands-Ponts', UTC_TIMESTAMP()), ('REGION', 'Guémon', UTC_TIMESTAMP()), ('REGION', 'Hambol', UTC_TIMESTAMP()),
 ('REGION', 'Haut-Sassandra', UTC_TIMESTAMP()), ('REGION', 'Iffou', UTC_TIMESTAMP()), ('REGION', 'Indénié-Djuablin', UTC_TIMESTAMP()),
 ('REGION', 'Kabadougou', UTC_TIMESTAMP()), ('REGION', 'La Mé', UTC_TIMESTAMP()), ('REGION', 'Lôh-Djiboua', UTC_TIMESTAMP()),
 ('REGION', 'Marahoué', UTC_TIMESTAMP()), ('REGION', 'Moronou', UTC_TIMESTAMP()), ('REGION', 'Nawa', UTC_TIMESTAMP()),
 ('REGION', 'N''Zi', UTC_TIMESTAMP()), ('REGION', 'Poro', UTC_TIMESTAMP()), ('REGION', 'San-Pédro', UTC_TIMESTAMP()),
 ('REGION', 'Sud-Comoé', UTC_TIMESTAMP()), ('REGION', 'Tchologo', UTC_TIMESTAMP()), ('REGION', 'Tonkpi', UTC_TIMESTAMP()),
 ('REGION', 'Worodougou', UTC_TIMESTAMP());

-- Principales villes, rattachees a leur region
INSERT INTO crm_referentiel (type_referentiel, libelle, parent_id, created_at)
SELECT 'VILLE', v.ville, r.id, UTC_TIMESTAMP()
FROM (SELECT 'Abidjan' AS ville, 'District autonome d''Abidjan' AS region
      UNION ALL SELECT 'Yamoussoukro', 'District autonome de Yamoussoukro'
      UNION ALL SELECT 'Bouaké', 'Gbêkê' UNION ALL SELECT 'Gagnoa', 'Gôh' UNION ALL SELECT 'Daloa', 'Haut-Sassandra'
      UNION ALL SELECT 'San-Pédro', 'San-Pédro' UNION ALL SELECT 'Korhogo', 'Poro' UNION ALL SELECT 'Man', 'Tonkpi'
      UNION ALL SELECT 'Divo', 'Lôh-Djiboua' UNION ALL SELECT 'Abengourou', 'Indénié-Djuablin' UNION ALL SELECT 'Soubré', 'Nawa'
      UNION ALL SELECT 'Agboville', 'Agnéby-Tiassa' UNION ALL SELECT 'Grand-Bassam', 'Sud-Comoé' UNION ALL SELECT 'Aboisso', 'Sud-Comoé'
      UNION ALL SELECT 'Dabou', 'Grands-Ponts' UNION ALL SELECT 'Bondoukou', 'Gontougo' UNION ALL SELECT 'Odienné', 'Kabadougou'
      UNION ALL SELECT 'Séguéla', 'Worodougou' UNION ALL SELECT 'Katiola', 'Hambol' UNION ALL SELECT 'Dimbokro', 'N''Zi'
      UNION ALL SELECT 'Adzopé', 'La Mé' UNION ALL SELECT 'Sassandra', 'Gbôklé' UNION ALL SELECT 'Daoukro', 'Iffou'
      UNION ALL SELECT 'Bouaflé', 'Marahoué' UNION ALL SELECT 'Duékoué', 'Guémon' UNION ALL SELECT 'Guiglo', 'Cavally'
      UNION ALL SELECT 'Ferkessédougou', 'Tchologo' UNION ALL SELECT 'Boundiali', 'Bagoué' UNION ALL SELECT 'Touba', 'Bafing'
      UNION ALL SELECT 'Toumodi', 'Bélier' UNION ALL SELECT 'Mankono', 'Béré' UNION ALL SELECT 'Bouna', 'Bounkani'
      UNION ALL SELECT 'Minignan', 'Folon' UNION ALL SELECT 'Bongouanou', 'Moronou') v
JOIN crm_referentiel r ON r.type_referentiel = 'REGION' AND r.libelle = v.region;

-- Communes d'Abidjan
INSERT INTO crm_referentiel (type_referentiel, libelle, parent_id, created_at)
SELECT 'COMMUNE', c.commune, v.id, UTC_TIMESTAMP()
FROM (SELECT 'Abobo' AS commune UNION ALL SELECT 'Adjamé' UNION ALL SELECT 'Anyama' UNION ALL SELECT 'Attécoubé' UNION ALL SELECT 'Bingerville'
      UNION ALL SELECT 'Cocody' UNION ALL SELECT 'Koumassi' UNION ALL SELECT 'Marcory' UNION ALL SELECT 'Plateau' UNION ALL SELECT 'Port-Bouët'
      UNION ALL SELECT 'Songon' UNION ALL SELECT 'Treichville' UNION ALL SELECT 'Yopougon') c
JOIN crm_referentiel v ON v.type_referentiel = 'VILLE' AND v.libelle = 'Abidjan';

-- Villes deja saisies sur les clients et absentes de la liste : reprises telles quelles, sans region
INSERT INTO crm_referentiel (type_referentiel, libelle, created_at)
SELECT DISTINCT 'VILLE', TRIM(c.ville), UTC_TIMESTAMP() FROM crm_client c
WHERE c.ville IS NOT NULL AND TRIM(c.ville) <> ''
  AND NOT EXISTS (SELECT 1 FROM crm_referentiel r WHERE r.type_referentiel = 'VILLE' AND r.libelle = TRIM(c.ville));

ALTER TABLE crm_client
    ADD COLUMN region_id BIGINT NULL AFTER sous_agence,
    ADD COLUMN ville_id BIGINT NULL AFTER region_id,
    ADD COLUMN commune_id BIGINT NULL AFTER ville_id,
    ADD COLUMN tournee_id BIGINT NULL AFTER commune_id,
    ADD COLUMN date_anniversaire DATE NULL AFTER tournee_id,
    ADD CONSTRAINT fk_crm_client_region FOREIGN KEY (region_id) REFERENCES crm_referentiel(id),
    ADD CONSTRAINT fk_crm_client_ville FOREIGN KEY (ville_id) REFERENCES crm_referentiel(id),
    ADD CONSTRAINT fk_crm_client_commune FOREIGN KEY (commune_id) REFERENCES crm_referentiel(id),
    ADD CONSTRAINT fk_crm_client_tournee FOREIGN KEY (tournee_id) REFERENCES crm_referentiel(id);

UPDATE crm_client c JOIN crm_referentiel v ON v.type_referentiel = 'VILLE' AND v.libelle = TRIM(c.ville)
SET c.ville_id = v.id, c.region_id = v.parent_id;

-- La ville texte est remplacee par la ville du referentiel (valeurs reprises ci-dessus)
ALTER TABLE crm_client DROP COLUMN ville;

-- Segments : un badge choisi dans un jeu fixe, affiche sur la fiche du client
ALTER TABLE crm_segment
    ADD COLUMN badge VARCHAR(30) NOT NULL DEFAULT 'ETOILE' AFTER libelle,
    ADD COLUMN modifie_par BIGINT NULL AFTER statut,
    ADD COLUMN updated_at DATETIME NULL AFTER created_at,
    ADD CONSTRAINT fk_crm_segment_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id);
UPDATE crm_segment SET badge = 'COURONNE' WHERE code = 'P';
UPDATE crm_segment SET badge = 'MEDAILLE' WHERE code = 'S';
UPDATE crm_segment SET badge = 'BOUCLIER' WHERE code = 'B';

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
VALUES ('REFERENTIELS', 'Référentiels', 'Tenir les régions, villes, communes, tournées et segments (avec leur badge)', 'Administration', NULL, 306);
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT rd.role_id, d.id, UTC_TIMESTAMP() FROM crm_role_droit rd JOIN crm_droit a ON a.id = rd.droit_id AND a.code = 'AGENCES'
JOIN crm_droit d ON d.code = 'REFERENTIELS';
