-- =====================================================================
-- UBIPHARM CRM - Canal SMS des relances, en 3e position apres WhatsApp et e-mail
-- Le SMS part par l'API HTTP d'un fournisseur (LeTexto par defaut). Chaque
-- fournisseur est decrit en base : adresse, en-tetes, gabarit du corps et
-- lecture des reponses. Un nouveau fournisseur se parametre depuis l'ecran
-- d'administration, sans developpement.
-- =====================================================================

ALTER TABLE crm_modele_message DROP CONSTRAINT ck_crm_modele_message_canal;
ALTER TABLE crm_modele_message ADD CONSTRAINT ck_crm_modele_message_canal CHECK (canal IN ('WHATSAPP', 'EMAIL', 'SMS'));

ALTER TABLE crm_campagne_message DROP CONSTRAINT ck_crm_cm_canal;
ALTER TABLE crm_campagne_message
    ADD COLUMN fournisseur_sms_id BIGINT AFTER objet,
    ADD COLUMN identifiant_sms VARCHAR(100) AFTER identifiant_whatsapp,
    ADD COLUMN nb_sms INT AFTER identifiant_sms,
    ADD KEY idx_crm_cm_sms (fournisseur_sms_id, identifiant_sms),
    ADD CONSTRAINT ck_crm_cm_canal CHECK (canal IN ('WHATSAPP', 'EMAIL', 'SMS'));

CREATE TABLE crm_fournisseur_sms (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                     VARCHAR(30)  NOT NULL,
    libelle                  VARCHAR(100) NOT NULL,
    actif                    BOOLEAN      NOT NULL DEFAULT TRUE,
    par_defaut               BOOLEAN      NOT NULL DEFAULT FALSE,
    expediteur               VARCHAR(20),
    cle_api                  VARCHAR(500),
    format_numero            VARCHAR(20)  NOT NULL DEFAULT 'INTERNATIONAL',
    -- Envoi : {cle} {expediteur} {numero} {message} {reference} {url_accuse}
    url_envoi                VARCHAR(500) NOT NULL,
    methode_envoi            VARCHAR(10)  NOT NULL DEFAULT 'POST',
    type_contenu             VARCHAR(100) NOT NULL DEFAULT 'application/json',
    entetes                  VARCHAR(2000),
    corps                    VARCHAR(4000),
    chemin_identifiant       VARCHAR(200),
    chemin_nb_sms            VARCHAR(200),
    chemin_erreur            VARCHAR(200),
    -- Statut d'un message, interroge si l'accuse n'arrive pas : {cle} {identifiant}
    url_statut               VARCHAR(500),
    chemin_statut            VARCHAR(200),
    -- Accuse de reception pousse par le fournisseur
    champ_identifiant_accuse VARCHAR(200),
    champ_statut_accuse      VARCHAR(200),
    correspondance_statuts   VARCHAR(1000),
    jeton_accuse             VARCHAR(64)  NOT NULL,
    -- Solde du compte : {cle}
    url_solde                VARCHAR(500),
    chemin_solde             VARCHAR(200),
    modifie_par              BIGINT,
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME,
    -- Un seul fournisseur par defaut : la colonne generee vaut 1 pour lui, NULL pour les autres
    defaut_unique            TINYINT GENERATED ALWAYS AS (CASE WHEN par_defaut THEN 1 END) STORED,
    CONSTRAINT uk_crm_fournisseur_sms_code UNIQUE (code),
    CONSTRAINT uk_crm_fournisseur_sms_defaut UNIQUE (defaut_unique),
    CONSTRAINT ck_crm_fournisseur_sms_methode CHECK (methode_envoi IN ('POST', 'GET', 'PUT')),
    CONSTRAINT ck_crm_fournisseur_sms_format CHECK (format_numero IN ('INTERNATIONAL', 'PLUS', 'LOCAL')),
    CONSTRAINT fk_crm_fournisseur_sms_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE crm_campagne_message
    ADD CONSTRAINT fk_crm_cm_fournisseur_sms FOREIGN KEY (fournisseur_sms_id) REFERENCES crm_fournisseur_sms (id);

-- LeTexto (Arolitec), d'apres sa documentation API v1 ; la cle API est saisie par l'administrateur
INSERT INTO crm_fournisseur_sms (code, libelle, actif, par_defaut, expediteur, format_numero, url_envoi, methode_envoi, type_contenu, entetes, corps,
                                 chemin_identifiant, chemin_nb_sms, chemin_erreur, url_statut, chemin_statut, champ_identifiant_accuse,
                                 champ_statut_accuse, correspondance_statuts, jeton_accuse, url_solde, chemin_solde, created_at)
VALUES ('LETEXTO', 'LeTexto', TRUE, TRUE, 'UBIPHARM', 'INTERNATIONAL',
        'https://apis.letexto.com/v1/messages/send', 'POST', 'application/json',
        'Authorization: Bearer {cle}',
        '{"from": "{expediteur}", "to": "{numero}", "content": "{message}", "dlrUrl": "{url_accuse}", "dlrMethod": "POST", "customData": "{reference}"}',
        'id', 'partCount', 'message',
        'https://apis.letexto.com/v1/messages/{identifiant}/status?token={cle}', 'status, statut, statuts',
        'id', 'statuts, status, statut',
        'PENDING=ENVOYE; SENT=ENVOYE; DELIVERED=DISTRIBUE; FAILED=ECHEC',
        REPLACE(UUID(), '-', ''),
        'https://apis.letexto.com/v1/users/balance?token={cle}', 'balance, solde, credit',
        UTC_TIMESTAMP());

INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('sms.envoi.delai_secondes', '1',    'Attente entre deux SMS', 'SMS', UTC_TIMESTAMP()),
 ('sms.envoi.plafond_jour',   '2000', 'SMS de relance par jour, toutes campagnes confondues', 'SMS', UTC_TIMESTAMP()),
 ('sms.envoi.heure_debut',    '8',    'Heure (Abidjan) à partir de laquelle les SMS partent', 'SMS', UTC_TIMESTAMP()),
 ('sms.envoi.heure_fin',      '18',   'Heure (Abidjan) après laquelle plus aucun SMS ne part', 'SMS', UTC_TIMESTAMP()),
 ('sms.envoi.pause_cle_refusee_minutes', '10', 'Attente avant un nouvel essai quand le fournisseur refuse la clé API', 'SMS', UTC_TIMESTAMP()),
 ('application.url_publique', '',     'Adresse HTTPS publique du CRM, pour les accusés de réception des SMS ; vide = statut interrogé chez le fournisseur', 'GENERAL', UTC_TIMESTAMP());
