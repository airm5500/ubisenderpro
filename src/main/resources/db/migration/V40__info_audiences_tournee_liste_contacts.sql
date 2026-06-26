-- =====================================================================
-- UbiSenderPro - Informations : audiences tournée / liste / contacts manuels
-- =====================================================================

-- Tournée sur le client (permet le ciblage « clients d'une tournée »).
ALTER TABLE usp_client ADD COLUMN tournee VARCHAR(150) AFTER region;

-- Valeurs de ciblage portées par la campagne.
ALTER TABLE usp_campagne
    ADD COLUMN tournee_cible VARCHAR(150) AFTER region_cible,
    ADD COLUMN contact_ids VARCHAR(2000) AFTER tournee_cible;

-- Sélection manuelle de contacts portée par l'information.
ALTER TABLE usp_info_evenement ADD COLUMN contact_ids VARCHAR(2000) AFTER liste_id;
