-- =====================================================================
-- UBIPHARM CRM - DG et Directeur Commercial en consultation seule (decision d'Airman du 22/10/2026,
-- conforme au cahier des charges revise). Les droits d'ecriture sont decoches, comme depuis l'ecran
-- Roles et droits : ils restent recochables par l'administrateur.
-- =====================================================================

UPDATE crm_role_droit rd
JOIN crm_role r ON r.id = rd.role_id
JOIN crm_droit d ON d.id = rd.droit_id
SET rd.actif = FALSE, rd.updated_at = UTC_TIMESTAMP()
WHERE r.code IN ('DG', 'DIR_COMMERCIAL')
  AND d.code IN ('AGENCES', 'REFERENTIELS', 'LISTES_GERER', 'PRODUITS_GERER', 'PROMOTIONS_GERER',
                 'CAMPAGNES_PREPARER', 'CAMPAGNES_VALIDER', 'CAMPAGNES_MARKETING_PREPARER', 'CAMPAGNES_MARKETING_VALIDER',
                 'MODELES_GERER', 'CLIENTS_GERER', 'FACTURES_GERER', 'IMPORTS', 'PORTEFEUILLES_AFFECTER', 'INTERIMS',
                 'ENVOI_MASSE', 'UTILISATEURS', 'ROLES', 'PARAMETRES', 'FOURNISSEURS_SMS', 'WHATSAPP_META')
  AND rd.actif = TRUE;

-- Les sessions ouvertes reprennent les nouveaux droits a la requete suivante
UPDATE crm_utilisateur u
SET u.droits_revises_le = UTC_TIMESTAMP(6)
WHERE EXISTS (SELECT 1 FROM crm_habilitation h JOIN crm_role r ON r.id = h.role_id
              WHERE h.utilisateur_id = u.id AND h.actif = TRUE AND r.code IN ('DG', 'DIR_COMMERCIAL'));
