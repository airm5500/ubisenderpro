-- =====================================================================
-- UBIPHARM CRM - Chaque module a ses listes de diffusion (decision d'Airman du 22/10/2026, Q7) :
-- celles du Recouvrement sont rangees dans « Relances » et ne se voient jamais du Marketing, et
-- inversement. Les listes deja creees restent au Marketing. Un meme nom peut servir dans chaque module.
-- =====================================================================

ALTER TABLE crm_liste_diffusion
    ADD COLUMN module VARCHAR(20) NOT NULL DEFAULT 'MARKETING' AFTER id,
    ADD CONSTRAINT ck_crm_liste_diffusion_module CHECK (module IN ('RECOUVREMENT', 'MARKETING')),
    DROP INDEX uk_crm_liste_diffusion_nom,
    ADD UNIQUE KEY uk_crm_liste_diffusion_module_nom (module, nom);

-- Droits des listes du Recouvrement, distincts de ceux du Marketing
INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, 'Relances', m.id, d.ordre FROM (
    SELECT 'LISTES_RELANCES' code, 'Listes de diffusion des relances' libelle, 'Consulter les listes du Recouvrement et leurs destinataires' description, 160 ordre
    UNION ALL SELECT 'LISTES_RELANCES_GERER', 'Créer et modifier les listes des relances', 'Critères, ajouts et exclusions des listes du Recouvrement', 170
) d JOIN crm_module m ON m.code = 'RECOUVREMENT';

-- Consultation : les profils qui voient les campagnes de relance, sauf l'agent (limite a son portefeuille)
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT DISTINCT r.id, d.id, UTC_TIMESTAMP()
FROM crm_role r
JOIN crm_role_droit rd ON rd.role_id = r.id AND rd.actif = TRUE
JOIN crm_droit c ON c.id = rd.droit_id AND c.code = 'CAMPAGNES'
JOIN crm_droit d ON d.code = 'LISTES_RELANCES'
WHERE r.code <> 'AGENT';

-- Gestion : les profils qui preparent les campagnes de relance, sauf l'agent
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT DISTINCT r.id, d.id, UTC_TIMESTAMP()
FROM crm_role r
JOIN crm_role_droit rd ON rd.role_id = r.id AND rd.actif = TRUE
JOIN crm_droit c ON c.id = rd.droit_id AND c.code = 'CAMPAGNES_PREPARER'
JOIN crm_droit d ON d.code = 'LISTES_RELANCES_GERER'
WHERE r.code <> 'AGENT';

UPDATE crm_utilisateur u
SET u.droits_revises_le = UTC_TIMESTAMP(6)
WHERE EXISTS (SELECT 1 FROM crm_habilitation h JOIN crm_role_droit rd ON rd.role_id = h.role_id
              JOIN crm_droit d ON d.id = rd.droit_id
              WHERE h.utilisateur_id = u.id AND h.actif = TRUE AND d.code IN ('LISTES_RELANCES', 'LISTES_RELANCES_GERER'));
