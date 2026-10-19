-- =====================================================================
-- UBIPHARM CRM - Promotions programmees et suspendues.
-- PROGRAMMEE : activee avant sa date de debut, elle s'ouvre d'office ce jour-la.
-- SUSPENDUE : desactivee a la main, plus proposee aux campagnes ; on la reprend.
-- =====================================================================

ALTER TABLE crm_promotion DROP CONSTRAINT ck_crm_promotion_statut;
ALTER TABLE crm_promotion ADD CONSTRAINT ck_crm_promotion_statut
    CHECK (statut IN ('BROUILLON', 'PROGRAMMEE', 'ACTIVE', 'SUSPENDUE', 'TERMINEE', 'ARCHIVEE'));
