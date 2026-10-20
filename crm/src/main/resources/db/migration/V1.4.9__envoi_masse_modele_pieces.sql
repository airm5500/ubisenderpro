-- =====================================================================
-- UBIPHARM CRM - Envoi WhatsApp en masse repris d'UbiSenderPro (demande d'Airman du 20/10/2026)
-- Modele de depart, variables du catalogue, pieces jointes (la 1re image porte le texte en legende),
-- clients choisis ou fichier importe en plus des numeros colles et de la liste, date de depart,
-- renvoi des echecs. Le debit reste celui de Parametres > WhatsApp.
-- =====================================================================

ALTER TABLE crm_envoi_masse
    ADD COLUMN modele_id BIGINT NULL AFTER contenu,
    ADD COLUMN depart_le DATETIME NULL AFTER modele_id,
    ADD CONSTRAINT fk_crm_envoi_masse_modele FOREIGN KEY (modele_id) REFERENCES crm_modele_message (id);

CREATE TABLE crm_envoi_masse_piece_jointe (
    envoi_masse_id BIGINT NOT NULL,
    piece_jointe_id BIGINT NOT NULL,
    ordre INT NOT NULL,
    PRIMARY KEY (envoi_masse_id, piece_jointe_id),
    CONSTRAINT fk_crm_empj_envoi FOREIGN KEY (envoi_masse_id) REFERENCES crm_envoi_masse (id),
    CONSTRAINT fk_crm_empj_piece FOREIGN KEY (piece_jointe_id) REFERENCES crm_piece_jointe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pieces jointes des envois en masse et des discussions : images, PDF, Excel, Word
ALTER TABLE crm_piece_jointe
    DROP CONSTRAINT ck_crm_piece_jointe_nature,
    ADD CONSTRAINT ck_crm_piece_jointe_nature CHECK (nature IN ('AFFICHE', 'LISTE_PRODUITS', 'DOCUMENT'));
