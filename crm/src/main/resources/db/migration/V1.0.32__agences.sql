-- =====================================================================
-- UBIPHARM CRM - Referentiel des agences administrable
-- Les 5 agences (Yopougon, Vridi, Centrale, Bouake, Gagnoa) sont livrees ;
-- la Direction Generale en cree, renomme ou archive d'autres. Une agence
-- n'est jamais supprimee : archivee, elle sort des listes de choix.
-- =====================================================================

ALTER TABLE crm_agence
    ADD COLUMN modifie_par BIGINT NULL AFTER statut,
    ADD COLUMN updated_at DATETIME NULL AFTER created_at,
    ADD CONSTRAINT fk_crm_agence_modifie FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    ADD CONSTRAINT ck_crm_agence_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'));

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
VALUES ('AGENCES', 'Agences', 'Créer, renommer et archiver les agences', 'Administration', NULL, 305);

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'AGENCES' WHERE r.code IN ('ADMIN', 'DG');
