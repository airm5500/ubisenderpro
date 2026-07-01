-- Trace de la (des) pièce(s) jointe(s) d'une relance (nom de fichier / relevé auto).
ALTER TABLE usp_rec_envoi
    ADD COLUMN piece_jointe VARCHAR(255) NULL;
