-- =====================================================================
-- UBIPHARM CRM - Parametres de l'entreprise, charte et archivage
-- Repris d'UbiSenderPro : site et lien de commande (variables de message),
-- logo et icone de l'application, mode d'envoi WhatsApp par defaut,
-- repertoire d'archivage des documents. Un parametre CHOIX porte ses
-- options (CODE=Libelle|...).
-- =====================================================================

ALTER TABLE crm_parametre ADD COLUMN options VARCHAR(500) NULL AFTER maximum;
ALTER TABLE crm_parametre DROP CONSTRAINT ck_crm_parametre_type;
ALTER TABLE crm_parametre ADD CONSTRAINT ck_crm_parametre_type CHECK (type_valeur IN ('TEXTE', 'ENTIER', 'HEURE', 'EMAIL', 'URL', 'COULEUR', 'LIEN', 'CHOIX',
        'OUI_NON', 'DOSSIER'));

UPDATE crm_parametre SET description = 'Adresse HTTPS publique du CRM (accusés des SMS, webhook Meta) ; vide = statut demandé au fournisseur'
WHERE cle = 'application.url_publique';

INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('entreprise.site_web', NULL, 'LIEN', NULL, FALSE, 'Site internet de l''entreprise, cité par {site_entreprise}', 'GENERAL', 3, UTC_TIMESTAMP()),
 ('entreprise.lien_commande', NULL, 'LIEN', NULL, FALSE, 'Lien de commande en ligne (Extranet), cité par {lien_commande}', 'GENERAL', 4, UTC_TIMESTAMP()),
 ('entreprise.telephone', NULL, 'TEXTE', NULL, FALSE, 'Téléphone de l''entreprise, cité par {telephone_entreprise}', 'GENERAL', 5, UTC_TIMESTAMP()),
 ('envoi.canal_defaut', 'WHATSAPP', 'CHOIX', 'WHATSAPP=WhatsApp|EMAIL=E-mail|SMS=SMS', TRUE,
  'Canal d''envoi proposé d''office pour un nouveau modèle de message', 'ENVOI', 1, UTC_TIMESTAMP()),
 ('whatsapp.mode_envoi_defaut', 'WA_WEB', 'CHOIX', 'WA_WEB=WhatsApp Web (QR code)|META=API officielle Meta', TRUE,
  'Mode d''envoi WhatsApp proposé d''office pour un nouveau numéro', 'ENVOI', 2, UTC_TIMESTAMP()),
 ('archivage.actif', 'OUI', 'OUI_NON', NULL, TRUE,
  'Copier dans le répertoire d''archivage chaque document reçu ou produit (imports, rejets, exports, affiches, listes de produits)', 'ARCHIVAGE', 1, UTC_TIMESTAMP()),
 ('archivage.repertoire', 'C:\\UBIPHARM-CRM\\donnees\\archives', 'DOSSIER', NULL, TRUE,
  'Répertoire d''archivage sur le serveur', 'ARCHIVAGE', 2, UTC_TIMESTAMP()),
 ('archivage.classement', 'TYPE_MOIS', 'CHOIX', 'TYPE_MOIS=Par type, puis par mois|MOIS_TYPE=Par mois, puis par type|TYPE=Par type seulement', TRUE,
  'Classement des documents dans le répertoire', 'ARCHIVAGE', 3, UTC_TIMESTAMP());

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('site_entreprise',      'Site internet de l''entreprise', 'calcul.site_entreprise',      'https://www.ubipharm.ci', UTC_TIMESTAMP()),
 ('lien_commande',        'Lien de commande en ligne',      'calcul.lien_commande',        'https://extranet.ubipharm.ci', UTC_TIMESTAMP()),
 ('telephone_entreprise', 'Téléphone de l''entreprise',     'calcul.telephone_entreprise', '27 21 00 00 00', UTC_TIMESTAMP());

-- Logo et icone de l'application : chaque depot est garde, le dernier actif s'affiche
CREATE TABLE crm_charte_fichier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    nom_fichier VARCHAR(255) NOT NULL,
    type_mime VARCHAR(100) NOT NULL,
    contenu MEDIUMBLOB NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    cree_par BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_charte_fichier (code, actif),
    CONSTRAINT fk_crm_charte_fichier_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_charte_fichier_code CHECK (code IN ('LOGO', 'ICONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
