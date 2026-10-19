-- =====================================================================
-- UBIPHARM CRM - Pieces jointes des campagnes Marketing
-- Une affiche (image ou PDF) choisie par l'auteur et, sur demande, la liste
-- Excel des produits des promotions citees, generee a la validation (et a
-- chaque envoi d'une campagne recurrente). Envoyees par WhatsApp et e-mail ;
-- le SMS n'en transporte pas. Rien n'est supprime.
-- =====================================================================

CREATE TABLE crm_piece_jointe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nature VARCHAR(20) NOT NULL,
    nom_fichier VARCHAR(200) NOT NULL,
    type_mime VARCHAR(100) NOT NULL,
    taille INT NOT NULL,
    contenu LONGBLOB NOT NULL,
    cree_par BIGINT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_crm_piece_jointe_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_piece_jointe_nature CHECK (nature IN ('AFFICHE', 'LISTE_PRODUITS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE crm_campagne
    ADD COLUMN affiche_id BIGINT NULL AFTER promotion_ids,
    ADD COLUMN joindre_produits BOOLEAN NOT NULL DEFAULT FALSE AFTER affiche_id,
    ADD COLUMN liste_produits_id BIGINT NULL AFTER joindre_produits,
    ADD CONSTRAINT fk_crm_campagne_affiche FOREIGN KEY (affiche_id) REFERENCES crm_piece_jointe(id),
    ADD CONSTRAINT fk_crm_campagne_liste_produits FOREIGN KEY (liste_produits_id) REFERENCES crm_piece_jointe(id);

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at)
VALUES ('marketing.affiche_taille_max_ko', '5000', 'ENTIER', 100, 10000, TRUE,
        'Taille maximale d''une affiche jointe aux campagnes, en Ko (WhatsApp refuse les images de plus de 5 Mo)', 'MARKETING', 2,
        UTC_TIMESTAMP());
