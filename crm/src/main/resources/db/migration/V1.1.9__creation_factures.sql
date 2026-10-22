-- =====================================================================
-- UBIPHARM CRM - Creation et modification d'une facture a la main : tout ce
-- qui s'importe doit aussi pouvoir se saisir (demande d'Airman, 19/10/2026).
-- =====================================================================

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'FACTURES_GERER', 'Créer et modifier les factures', 'Saisir une facture ou un avoir sans import, corriger son solde',
       'Créances', m.id, 25
FROM crm_module m WHERE m.code = 'RECOUVREMENT';

-- Ceux qui importent peuvent aussi saisir une facture
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT rd.role_id, n.id, UTC_TIMESTAMP()
FROM crm_role_droit rd
JOIN crm_droit i ON i.id = rd.droit_id AND i.code = 'IMPORTS'
JOIN crm_droit n ON n.code = 'FACTURES_GERER'
WHERE rd.actif = TRUE;
