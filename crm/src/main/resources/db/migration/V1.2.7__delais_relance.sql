-- =====================================================================
-- UBIPHARM CRM - Delais de relance par agence et par segment client
-- Les parametres campagne.* restent les delais par defaut ; une regle
-- propre a une agence, a un segment (Platinum, Silver, Bronze) ou aux
-- deux les remplace. Une valeur vide dans une regle reprend le niveau
-- au-dessus. Ordre : agence + segment, agence, segment, defaut.
-- =====================================================================

CREATE TABLE crm_delai_relance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agence_id BIGINT NULL,
    segment VARCHAR(20) NULL,
    avant_jours_max INT NULL,
    avant_jours_min INT NULL,
    retard_jours_min INT NULL,
    intervalle_jours INT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_delai_relance (statut, agence_id, segment),
    CONSTRAINT fk_crm_dr_agence FOREIGN KEY (agence_id) REFERENCES crm_agence(id),
    CONSTRAINT fk_crm_dr_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_dr_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('campagne.apres_echeance_jours_min', '1', 'ENTIER', 0, 365, TRUE,
  'Relance après échéance : premier jour, en jours de retard (J+n)', 'CAMPAGNE', 3, UTC_TIMESTAMP()),
 ('campagne.relance_intervalle_jours', '0', 'ENTIER', 0, 90, TRUE,
  'Jours minimum entre deux relances d''une même facture (0 = pas de minimum)', 'CAMPAGNE', 4, UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'DELAIS_RELANCE', 'Délais de relance', 'Régler les délais de relance par agence et par segment client', 'Relances', m.id, 28
FROM crm_module m WHERE m.code = 'RECOUVREMENT';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'DELAIS_RELANCE'
WHERE r.code IN ('ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE');
