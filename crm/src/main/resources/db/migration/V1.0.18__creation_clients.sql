-- =====================================================================
-- UBIPHARM CRM - Creation et modification d'un client a la main, pour les
-- nouveaux clients qui arrivent un par un entre deux imports.
-- Comme les imports, ce droit vaut quel que soit le module.
-- =====================================================================

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
VALUES ('CLIENTS_GERER', 'Créer et modifier les clients', 'Ajouter un nouveau client sans import, corriger sa fiche', 'Créances', NULL, 35);

-- Ceux qui importent les clients peuvent aussi en creer un a la main
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT rd.role_id, n.id, UTC_TIMESTAMP()
FROM crm_role_droit rd
JOIN crm_droit i ON i.id = rd.droit_id AND i.code = 'IMPORTS'
JOIN crm_droit n ON n.code = 'CLIENTS_GERER'
WHERE rd.actif = TRUE;
