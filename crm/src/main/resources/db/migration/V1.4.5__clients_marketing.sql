-- =====================================================================
-- UBIPHARM CRM - Menu Clients aussi en Marketing (demande d'Airman du 19/10/2026)
-- La base clients est commune : le droit CLIENTS n'est plus reserve au Recouvrement
-- et le charge marketing le recoit.
-- =====================================================================

UPDATE crm_droit SET module_id = NULL WHERE code = 'CLIENTS';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'CLIENTS'
WHERE r.code = 'CHARGE_MARKETING'
  AND NOT EXISTS (SELECT 1 FROM crm_role_droit x WHERE x.role_id = r.id AND x.droit_id = d.id);
