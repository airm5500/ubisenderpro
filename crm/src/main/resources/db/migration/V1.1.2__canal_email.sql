-- =====================================================================
-- UBIPHARM CRM - Canal e-mail des relances, en plus de WhatsApp
-- Le canal est porte par le modele : une campagne prend celui de son modele.
-- L'e-mail part de l'adresse d'envoi d'UBIPHARM ; les reponses vont a
-- l'adresse de l'agent du client (Reply-To) quand il en a une.
-- =====================================================================

ALTER TABLE crm_modele_message
    ADD COLUMN canal VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP' AFTER type_campagne,
    ADD COLUMN objet VARCHAR(255) AFTER canal,
    ADD CONSTRAINT ck_crm_modele_message_canal CHECK (canal IN ('WHATSAPP', 'EMAIL'));

ALTER TABLE crm_campagne_message
    ADD COLUMN canal VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP' AFTER client_id,
    ADD COLUMN email_destinataire VARCHAR(255) AFTER numero_destinataire,
    ADD COLUMN email_reponse VARCHAR(255) AFTER numero_expediteur,
    ADD COLUMN objet VARCHAR(255) AFTER email_reponse,
    ADD KEY idx_crm_cm_canal (canal, statut),
    ADD CONSTRAINT ck_crm_cm_canal CHECK (canal IN ('WHATSAPP', 'EMAIL'));

-- Le serveur SMTP (hote, port, compte) est dans la configuration (spring.mail.*), pas en base
INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('email.expediteur',            '',    'Adresse d''envoi des relances (From) ; vide = celle du compte SMTP', 'EMAIL', UTC_TIMESTAMP()),
 ('email.nom_expediteur',        'UBIPHARM CI - Recouvrement', 'Nom affiché comme expéditeur', 'EMAIL', UTC_TIMESTAMP()),
 ('email.envoi.delai_secondes',  '2',   'Attente entre deux e-mails', 'EMAIL', UTC_TIMESTAMP()),
 ('email.envoi.plafond_jour',    '1000', 'E-mails de relance par jour, toutes campagnes confondues', 'EMAIL', UTC_TIMESTAMP()),
 ('email.envoi.heure_debut',     '8',   'Heure (Abidjan) à partir de laquelle les e-mails partent', 'EMAIL', UTC_TIMESTAMP()),
 ('email.envoi.heure_fin',       '18',  'Heure (Abidjan) après laquelle plus aucun e-mail ne part', 'EMAIL', UTC_TIMESTAMP());
