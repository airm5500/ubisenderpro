-- Aligne le ciblage des événements de disponibilité sur celui des informations
-- clients (mêmes options d'audience) : région, tournée, liste, contacts manuels.
ALTER TABLE usp_dispo_evenement ADD COLUMN region VARCHAR(150) NULL;
ALTER TABLE usp_dispo_evenement ADD COLUMN tournee VARCHAR(150) NULL;
ALTER TABLE usp_dispo_evenement ADD COLUMN liste_id BIGINT NULL;
ALTER TABLE usp_dispo_evenement ADD COLUMN contact_ids VARCHAR(2000) NULL;
