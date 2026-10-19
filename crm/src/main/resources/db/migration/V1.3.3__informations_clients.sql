-- =====================================================================
-- UBIPHARM CRM - Informations clients et alertes operationnelles (Marketing)
-- Repris d'UbiSenderPro : problemes de livraison (retard, tournee modifiee
-- ou annulee, reprise), gardes et jours feries, horaires, fermeture,
-- information generale, alerte urgente, retrait de produit. Une information
-- porte son contexte (heure estimee, pharmacien de garde...) ; la campagne
-- qui l'annonce la cite, se programme et se valide comme les autres.
-- Une ligne du modele dont la variable d'information est vide disparait.
-- =====================================================================

CREATE TABLE crm_information (
    id BIGINT NOT NULL AUTO_INCREMENT,
    titre VARCHAR(200) NOT NULL,
    type_information VARCHAR(40) NOT NULL,
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    message TEXT NULL,
    agence_id BIGINT NULL,
    tournee VARCHAR(150) NULL,
    signataire VARCHAR(150) NULL,
    fin_validite DATE NULL,
    -- Livraison
    date_livraison DATE NULL,
    creneau VARCHAR(100) NULL,
    heure_initiale VARCHAR(20) NULL,
    nouvelle_heure VARCHAR(20) NULL,
    cause_communicable VARCHAR(255) NULL,
    cause_interne VARCHAR(255) NULL,
    date_resolution DATE NULL,
    -- Garde et jour ferie
    jour_ferie VARCHAR(150) NULL,
    date_garde DATE NULL,
    heure_limite_commande VARCHAR(20) NULL,
    consignes_livraison VARCHAR(500) NULL,
    pharmacien_garde VARCHAR(150) NULL,
    telephone_pharmacien VARCHAR(40) NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_information (statut, type_information),
    CONSTRAINT fk_crm_information_agence FOREIGN KEY (agence_id) REFERENCES crm_agence(id),
    CONSTRAINT fk_crm_information_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_information_modifie FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_information_type CHECK (type_information IN ('RETARD_LIVRAISON', 'MODIFICATION_TOURNEE', 'ANNULATION_TOURNEE',
        'REPRISE_LIVRAISON', 'INFORMATION_GARDE', 'INFORMATION_JOUR_FERIE', 'MODIFICATION_HORAIRES', 'FERMETURE_AGENCE',
        'INFORMATION_GENERALE', 'ALERTE_URGENTE', 'RETRAIT_PRODUIT')),
    CONSTRAINT ck_crm_information_priorite CHECK (priorite IN ('NORMALE', 'IMPORTANTE', 'URGENTE', 'CRITIQUE')),
    CONSTRAINT ck_crm_information_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE crm_campagne
    ADD COLUMN information_id BIGINT NULL AFTER evenement_id,
    ADD CONSTRAINT fk_crm_campagne_information FOREIGN KEY (information_id) REFERENCES crm_information (id),
    DROP CONSTRAINT ck_crm_campagne_type,
    ADD CONSTRAINT ck_crm_campagne_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION', 'EVENEMENT', 'INFORMATION'));

-- Un modele d'information peut servir un type precis : la campagne le propose d'office
ALTER TABLE crm_modele_message
    ADD COLUMN type_information VARCHAR(40) NULL AFTER type_campagne,
    DROP CONSTRAINT ck_crm_modele_message_type,
    ADD CONSTRAINT ck_crm_modele_message_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION', 'EVENEMENT', 'INFORMATION'));

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('information_titre',            'Titre de l''information',                  'calcul.information_titre',            'Retard camion Yopougon', UTC_TIMESTAMP()),
 ('information_message',          'Texte libre de l''information',            'calcul.information_message',          'L''agence ouvre à 9 h ce samedi.', UTC_TIMESTAMP()),
 ('agence_ou_tournee',            'Tournée concernée, sinon agence',          'calcul.agence_ou_tournee',            'Tournée Yopougon Nord', UTC_TIMESTAMP()),
 ('tournee',                      'Tournée concernée',                        'calcul.tournee',                      'Tournée Yopougon Nord', UTC_TIMESTAMP()),
 ('date_livraison',               'Date de livraison concernée',              'calcul.date_livraison',               '20/10/2026', UTC_TIMESTAMP()),
 ('creneau',                      'Créneau de livraison',                     'calcul.creneau',                      'matin', UTC_TIMESTAMP()),
 ('nouvelle_estimation_livraison','Phrase « Nouvelle estimation de livraison »','calcul.nouvelle_estimation_livraison','Nouvelle estimation de livraison : 15 h', UTC_TIMESTAMP()),
 ('cause_retard',                 'Cause communiquée au client',              'calcul.cause_retard',                 'panne du camion', UTC_TIMESTAMP()),
 ('date_resolution',              'Date estimée de résolution',               'calcul.date_resolution',              '21/10/2026', UTC_TIMESTAMP()),
 ('jour_ferie',                   'Jour férié ou occasion de la garde',       'calcul.jour_ferie',                   'la Tabaski', UTC_TIMESTAMP()),
 ('date_garde',                   'Date de la garde',                         'calcul.date_garde',                   '27/05/2026', UTC_TIMESTAMP()),
 ('heure_limite_commande',        'Heure limite de commande',                 'calcul.heure_limite_commande',        '11 h', UTC_TIMESTAMP()),
 ('consignes_livraison',          'Consignes de livraison',                   'calcul.consignes_livraison',          'livraison unique à 14 h', UTC_TIMESTAMP()),
 ('pharmacien_garde',             'Pharmacien de garde',                      'calcul.pharmacien_garde',             'Dr Kouassi', UTC_TIMESTAMP()),
 ('telephone_pharmacien',         'Téléphone du pharmacien de garde',         'calcul.telephone_pharmacien',         '07 07 00 00 00', UTC_TIMESTAMP()),
 ('direction_signataire',         'Signataire (sinon « Direction » + agence)','calcul.direction_signataire',         'Direction Yopougon', UTC_TIMESTAMP());

-- Modeles de depart (UbiSenderPro), modifiables par le Marketing
INSERT INTO crm_modele_message (module, nom, type_campagne, type_information, canal, contenu, cree_par, created_at)
SELECT 'MARKETING', m.nom, 'INFORMATION', m.type_information, 'WHATSAPP', m.contenu, u.id, UTC_TIMESTAMP() FROM (
    SELECT 'Info · Retard de livraison' nom, 'RETARD_LIVRAISON' type_information,
           '📢 *UBI INFO · LIVRAISON*\n\nCher client *{nom_pharmacie}*,\n\nNous rencontrons actuellement un retard sur certaines livraisons concernant *{agence_ou_tournee}*.\nCause : {cause_retard}\n\nNos équipes sont pleinement mobilisées afin de vous livrer dans les meilleurs délais.\n\n{nouvelle_estimation_livraison}\n\nNous vous présentons nos sincères excuses pour ce contretemps et vous remercions pour votre patience et votre compréhension.\n\n*Merci pour votre confiance.*\n\n*{direction_signataire}*' contenu
    UNION ALL SELECT 'Info · Mise à jour livraison', 'MODIFICATION_TOURNEE',
           '📢 *UBI INFO · MISE À JOUR LIVRAISON*\n\nCher client *{nom_pharmacie}*,\n\nMise à jour concernant la livraison sur *{agence_ou_tournee}*.\n\n{nouvelle_estimation_livraison}\n\nNous restons mobilisés et vous tiendrons informé de toute évolution.\n\n*Merci pour votre confiance.*\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Annulation de tournée', 'ANNULATION_TOURNEE',
           '📢 *UBI INFO · TOURNÉE*\n\nCher client *{nom_pharmacie}*,\n\nNous vous informons de l''annulation de la tournée *{tournee}*.\nCause : {cause_retard}\n\nNos équipes reviennent vers vous dans les meilleurs délais pour la reprogrammation.\n\nNous vous présentons nos excuses pour la gêne occasionnée.\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Reprise des livraisons', 'REPRISE_LIVRAISON',
           '📢 *UBI INFO · REPRISE DES LIVRAISONS*\n\nCher client *{nom_pharmacie}*,\n\nBonne nouvelle : les livraisons sur *{agence_ou_tournee}* reprennent normalement.\n\nNous vous remercions pour votre patience et votre compréhension.\n\n💻 Vous pouvez transmettre vos commandes via *EXTRANET*.\n\n*Merci pour votre confiance.*\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Information de garde', 'INFORMATION_GARDE',
           '📢 *RAPPEL · INFORMATION DE GARDE*\n\nCher client *{nom_pharmacie}*,\n\nÀ l''occasion de *{jour_ferie}*, nous vous invitons à transmettre vos commandes à l''agence de *{agence}* avant *{heure_limite_commande}*.\n\n📦 Modalités de livraison : *{consignes_livraison}*\n\n💻 Merci de privilégier la saisie de vos commandes via *EXTRANET*.\n\nToute l''équipe UBIPHARM vous souhaite une excellente garde et une bonne fête.\n\nPharmacien de garde : *{pharmacien_garde}*\n📞 *{telephone_pharmacien}*\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Jour férié', 'INFORMATION_JOUR_FERIE',
           '📢 *UBI INFO · JOUR FÉRIÉ*\n\nCher client *{nom_pharmacie}*,\n\nÀ l''occasion de *{jour_ferie}*, l''organisation des commandes et livraisons est adaptée.\n\n📦 Modalités : *{consignes_livraison}*\n\n💻 Merci de transmettre vos commandes via *EXTRANET* avant *{heure_limite_commande}*.\n\nToute l''équipe UBIPHARM vous remercie.\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Modification des horaires', 'MODIFICATION_HORAIRES',
           '📢 *UBI INFO · HORAIRES*\n\nCher client *{nom_pharmacie}*,\n\nNous vous informons d''une modification des horaires de l''agence de *{agence}*.\n\n{information_message}\n\nMerci de votre compréhension.\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Fermeture d''agence', 'FERMETURE_AGENCE',
           '📢 *UBI INFO · FERMETURE*\n\nCher client *{nom_pharmacie}*,\n\nL''agence de *{agence}* sera fermée.\n\n{information_message}\n\n💻 Vous pouvez continuer à transmettre vos commandes via *EXTRANET*.\n\nMerci de votre compréhension.\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Information générale', 'INFORMATION_GENERALE',
           '📢 *UBI INFO*\n\nCher client *{nom_pharmacie}*,\n\n{information_message}\n\n*Merci pour votre confiance.*\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Alerte urgente', 'ALERTE_URGENTE',
           '🚨 *UBI ALERTE*\n\nCher client *{nom_pharmacie}*,\n\n{information_message}\n\nMerci de votre attention.\n\n*{direction_signataire}*'
    UNION ALL SELECT 'Info · Retrait de produit', 'RETRAIT_PRODUIT',
           '⚠️ *UBI INFO · RETRAIT DE PRODUIT*\n\nCher client *{nom_pharmacie}*,\n\nNous vous informons du *retrait* du produit suivant :\n*{information_message}*\n\nPar mesure de précaution, nous vous invitons à *cesser toute dispensation* de ce produit et à *isoler les unités* encore en votre possession.\n\nNos équipes reviennent vers vous pour les modalités de retour ou d''échange.\n\nMerci pour votre vigilance et votre confiance.\n\n*{direction_signataire}*'
) m JOIN crm_utilisateur u ON u.login = 'admin';

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'INFORMATIONS', 'Informations clients', 'Saisir les problèmes de livraison, gardes, horaires et alertes annoncés aux clients', 'Campagnes', m.id, 146
FROM crm_module m WHERE m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'INFORMATIONS'
WHERE r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE', 'CHARGE_MARKETING');
