-- =====================================================================
-- UBIPHARM CRM - Alignement sur la base clients reelle (fichier du 17/09/2026)
-- Colonnes : Numero de compte, Agce, Sgce, Seg, Nom, Adresse1, WhatsappMAJ,
-- PharmacienTitulaire, PharmacienAss., Chargedecde, Comptable, E-MailMAJ, Commentaire
-- =====================================================================

ALTER TABLE crm_client
    ADD COLUMN sous_agence VARCHAR(30) AFTER agence_id,
    ADD COLUMN adresse VARCHAR(255) AFTER ville,
    ADD COLUMN email VARCHAR(255) AFTER adresse,
    ADD COLUMN commentaire VARCHAR(1000) AFTER email;

-- Code d'agence tel qu'il figure dans les fichiers UBIPHARM (une lettre)
ALTER TABLE crm_agence
    ADD COLUMN code_import VARCHAR(30) AFTER code,
    ADD UNIQUE KEY uk_crm_agence_code_import (code_import);

-- Seule la lettre B apparait dans le fichier recu ; Bouake est une hypothese a confirmer.
UPDATE crm_agence SET code_import = 'B' WHERE code = 'BKE';

-- Role du numero dans la fiche client : PRINCIPAL (numero WhatsApp de relance), TITULAIRE,
-- ASSISTANT, CHARGE_COMMANDE, COMPTABLE, SECONDAIRE
UPDATE crm_contact SET fonction = 'PRINCIPAL' WHERE rang = 1 AND fonction IS NULL;
UPDATE crm_contact SET fonction = 'SECONDAIRE' WHERE rang = 2 AND fonction IS NULL;
