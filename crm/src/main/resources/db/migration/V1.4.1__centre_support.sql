-- =====================================================================
-- UBIPHARM CRM - Centre de support
-- Tickets d'assistance avec leur conversation, journal des erreurs
-- inattendues (serveur et navigateur) dedoublonnees par signature, FAQ.
-- Rien ne se supprime : un ticket se ferme, une question de FAQ s'archive.
-- Seul le journal des erreurs est purge selon support.retention_jours.
-- =====================================================================

CREATE TABLE crm_support_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sujet VARCHAR(200) NOT NULL,
    categorie VARCHAR(20) NOT NULL,
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    statut VARCHAR(20) NOT NULL DEFAULT 'OUVERT',
    auteur_id BIGINT NOT NULL,
    assigne_id BIGINT NULL,
    modifie_par BIGINT NULL,
    resolu_le DATETIME NULL,
    ferme_le DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_support_ticket_auteur (auteur_id, statut),
    KEY idx_crm_support_ticket_statut (statut, updated_at),
    CONSTRAINT fk_crm_support_ticket_auteur FOREIGN KEY (auteur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_support_ticket_assigne FOREIGN KEY (assigne_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_support_ticket_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_support_ticket_categorie CHECK (categorie IN ('QUESTION', 'INCIDENT', 'DEMANDE')),
    CONSTRAINT ck_crm_support_ticket_priorite CHECK (priorite IN ('NORMALE', 'HAUTE', 'URGENTE')),
    CONSTRAINT ck_crm_support_ticket_statut CHECK (statut IN ('OUVERT', 'EN_COURS', 'RESOLU', 'FERME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Conversation d'un ticket ; SYSTEME = changement de statut ou d'affectation trace dans le fil
CREATE TABLE crm_support_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    auteur_id BIGINT NOT NULL,
    type_message VARCHAR(20) NOT NULL DEFAULT 'MESSAGE',
    contenu TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_support_message_ticket (ticket_id, id),
    CONSTRAINT fk_crm_support_message_ticket FOREIGN KEY (ticket_id) REFERENCES crm_support_ticket(id),
    CONSTRAINT fk_crm_support_message_auteur FOREIGN KEY (auteur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_support_message_type CHECK (type_message IN ('MESSAGE', 'SYSTEME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Une ligne par erreur distincte (signature = empreinte du type et du message sans chiffres)
CREATE TABLE crm_support_erreur (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signature CHAR(64) NOT NULL,
    origine VARCHAR(20) NOT NULL,
    type_erreur VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NULL,
    detail TEXT NULL,
    chemin VARCHAR(500) NULL,
    utilisateur_id BIGINT NULL,
    occurrences INT NOT NULL DEFAULT 1,
    premiere_le DATETIME NOT NULL,
    derniere_le DATETIME NOT NULL,
    traite BOOLEAN NOT NULL DEFAULT FALSE,
    traite_par BIGINT NULL,
    traite_le DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_support_erreur_signature (signature),
    KEY idx_crm_support_erreur_derniere (traite, derniere_le),
    CONSTRAINT fk_crm_support_erreur_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_support_erreur_traite_par FOREIGN KEY (traite_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_support_erreur_origine CHECK (origine IN ('SERVEUR', 'NAVIGATEUR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE crm_support_faq (
    id BIGINT NOT NULL AUTO_INCREMENT,
    categorie VARCHAR(100) NOT NULL,
    question VARCHAR(300) NOT NULL,
    reponse TEXT NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_support_faq_statut (statut, categorie, ordre),
    CONSTRAINT fk_crm_support_faq_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_support_faq_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Droit de traitement : tous les tickets, diagnostic, sante, edition de la FAQ ; ouvrir un ticket reste permis a tous
INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
VALUES ('SUPPORT', 'Centre de support', 'Traiter tous les tickets, consulter le diagnostic et la santé de l''application, tenir la FAQ',
        'Administration', NULL, 307);
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'SUPPORT' WHERE r.code = 'ADMIN';

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('support.retention_jours', '90', 'ENTIER', 1, 3650, TRUE,
  'Durée de conservation (jours) des erreurs du diagnostic après leur dernière survenue', 'SUPPORT', 1, UTC_TIMESTAMP()),
 ('support.capture_max_minute', '10', 'ENTIER', 1, 1000, TRUE,
  'Nombre maximal d''enregistrements par minute d''une même erreur (au-delà, les répétitions sont ignorées)', 'SUPPORT', 2, UTC_TIMESTAMP()),
 ('support.espace_disque_min_mo', '1024', 'ENTIER', 1, 1000000, TRUE,
  'Espace libre minimal (Mo) du répertoire d''archivage en dessous duquel la santé passe en alerte', 'SUPPORT', 3, UTC_TIMESTAMP());

INSERT INTO crm_support_faq (categorie, question, reponse, ordre, created_at) VALUES
 ('Compte', 'J''ai oublié mon mot de passe, que faire ?',
  'Demandez à l''administrateur de réinitialiser votre mot de passe dans Utilisateurs et habilitations. Vous recevrez un mot de passe provisoire à remplacer à la première connexion.', 1, UTC_TIMESTAMP()),
 ('Support', 'Comment signaler un problème ?',
  'Ouvrez un ticket dans Centre de support, onglet Mes tickets : décrivez ce que vous faisiez, ce que vous attendiez et ce qui s''est passé. Si un message d''erreur affiche une référence, recopiez-la.', 2, UTC_TIMESTAMP());
