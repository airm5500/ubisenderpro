-- Nom de l'officine / entreprise du client, distinct du nom du client (personne).
ALTER TABLE usp_client
    ADD COLUMN entreprise VARCHAR(255) NULL;
