-- =====================================================================
-- UbiSenderPro - V9 : stockage des fichiers média importés (en-tête de
-- modèle, pièces jointes). Le fichier est servi par une URL publique que
-- WhatsApp peut récupérer (envoi de média par lien).
-- =====================================================================

CREATE TABLE usp_media_fichier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom_fichier VARCHAR(255),
    mime_type VARCHAR(100),
    taille BIGINT,
    contenu LONGBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
