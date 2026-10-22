-- =====================================================================
-- UBIPHARM CRM - Campagnes de relance (module Recouvrement)
-- Cahier des charges 4.4 a 4.14 : un message par facture, envoye depuis le
-- numero de l'agent du client ; modeles administres par la Direction du
-- Recouvrement ; aucune campagne executee sans validation prealable.
-- Rien n'est supprime : modeles archives, campagnes annulees.
-- =====================================================================

-- Variables utilisables dans les modeles : {code} -> champ de la base.
-- source = client.<colonne>, facture.<colonne> ou calcul.<nom> ; une colonne
-- ajoutee plus tard a crm_client ou crm_facture devient utilisable sans developpement.
CREATE TABLE crm_variable_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    source VARCHAR(100) NOT NULL,
    exemple VARCHAR(100),
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_variable_code (code),
    CONSTRAINT ck_crm_variable_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('code_client',       'Code client',                 'client.code',            'C00125',               UTC_TIMESTAMP()),
 ('numero_compte',     'Numero de compte',            'client.code',            'C00125',               UTC_TIMESTAMP()),
 ('nom_client',        'Nom du client (pharmacien)',  'client.nom_pharmacien',  'Dr KOUASSI Aya',       UTC_TIMESTAMP()),
 ('nom_pharmacie',     'Nom de la pharmacie',         'client.raison_sociale',  'Pharmacie du Plateau', UTC_TIMESTAMP()),
 ('agence',            'Agence',                      'calcul.agence',          'Yopougon',             UTC_TIMESTAMP()),
 ('sous_type',         'Sous-type de compte',         'calcul.sous_type',       'Normal',               UTC_TIMESTAMP()),
 ('reference_facture', 'Reference de la facture',     'facture.reference',      'FA-2026-00412',        UTC_TIMESTAMP()),
 ('date_facture',      'Date de facture',             'facture.date_facture',   '01/10/2026',           UTC_TIMESTAMP()),
 ('date_echeance',     'Date d''echeance',            'facture.date_echeance',  '31/10/2026',           UTC_TIMESTAMP()),
 ('montant',           'Montant de la facture',       'facture.montant',        '150 000',              UTC_TIMESTAMP()),
 ('solde',             'Solde restant du',            'facture.solde',          '120 000',              UTC_TIMESTAMP()),
 ('jours_retard',      'Jours de retard',             'calcul.jours_retard',    '12',                   UTC_TIMESTAMP()),
 ('jours_avant_echeance', 'Jours avant l''echeance',  'calcul.jours_avant_echeance', '3',               UTC_TIMESTAMP()),
 ('agent_nom',         'Nom de l''agent',             'calcul.agent_nom',       'KONE Ibrahim',         UTC_TIMESTAMP()),
 ('agent_numero',      'Numero WhatsApp de l''agent', 'calcul.agent_numero',    '2250700000001',        UTC_TIMESTAMP());

-- Modeles de messages : crees et modifies par la Direction du Recouvrement uniquement
CREATE TABLE crm_modele_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    type_campagne VARCHAR(30) NOT NULL,
    contenu TEXT NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_modele_message_nom (nom),
    CONSTRAINT fk_crm_modele_message_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_modele_message_modifie FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_modele_message_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE')),
    CONSTRAINT ck_crm_modele_message_statut CHECK (statut IN ('ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BROUILLON -> A_VALIDER -> VALIDEE -> EN_COURS -> TERMINEE ; ANNULEE a tout moment avant la fin.
-- Le perimetre du createur (agences, agents) est fige a la creation : la selection
-- ne change pas selon la personne qui valide.
CREATE TABLE crm_campagne (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    type_campagne VARCHAR(30) NOT NULL,
    modele_id BIGINT NOT NULL,
    criteres TEXT NOT NULL,
    date_execution DATETIME,
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    perimetre_agences VARCHAR(500),
    perimetre_agents VARCHAR(500),
    nb_clients INT NOT NULL DEFAULT 0,
    nb_factures INT NOT NULL DEFAULT 0,
    nb_messages INT NOT NULL DEFAULT 0,
    nb_exclus INT NOT NULL DEFAULT 0,
    nb_envoyes INT NOT NULL DEFAULT 0,
    nb_echecs INT NOT NULL DEFAULT 0,
    cree_par BIGINT NOT NULL,
    soumise_le DATETIME,
    valide_par BIGINT,
    valide_le DATETIME,
    motif_rejet VARCHAR(500),
    rejete_par BIGINT,
    lance_le DATETIME,
    termine_le DATETIME,
    annule_par BIGINT,
    annule_le DATETIME,
    motif_annulation VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_crm_campagne_statut (statut, date_execution),
    KEY idx_crm_campagne_cree (cree_par),
    CONSTRAINT fk_crm_campagne_modele FOREIGN KEY (modele_id) REFERENCES crm_modele_message(id),
    CONSTRAINT fk_crm_campagne_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_campagne_valide FOREIGN KEY (valide_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_campagne_rejete FOREIGN KEY (rejete_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_campagne_annule FOREIGN KEY (annule_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_campagne_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE')),
    CONSTRAINT ck_crm_campagne_statut CHECK (statut IN ('BROUILLON', 'A_VALIDER', 'VALIDEE', 'EN_COURS', 'TERMINEE', 'ANNULEE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un message par facture, fige a la validation. EXCLU = facture retenue par les criteres
-- mais impossible a relancer (pas d'agent, pas de numero...), avec son motif.
CREATE TABLE crm_campagne_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campagne_id BIGINT NOT NULL,
    facture_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    contact_id BIGINT,
    numero_destinataire VARCHAR(30),
    agent_id BIGINT,
    numero_expediteur VARCHAR(30),
    contenu TEXT,
    statut VARCHAR(20) NOT NULL,
    motif VARCHAR(255),
    identifiant_whatsapp VARCHAR(100),
    envoye_le DATETIME,
    distribue_le DATETIME,
    lu_le DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_campagne_message (campagne_id, facture_id),
    KEY idx_crm_cm_statut (campagne_id, statut),
    KEY idx_crm_cm_facture (facture_id, statut),
    KEY idx_crm_cm_agent (agent_id, statut),
    CONSTRAINT fk_crm_cm_campagne FOREIGN KEY (campagne_id) REFERENCES crm_campagne(id),
    CONSTRAINT fk_crm_cm_facture FOREIGN KEY (facture_id) REFERENCES crm_facture(id),
    CONSTRAINT fk_crm_cm_client FOREIGN KEY (client_id) REFERENCES crm_client(id),
    CONSTRAINT fk_crm_cm_contact FOREIGN KEY (contact_id) REFERENCES crm_contact(id),
    CONSTRAINT fk_crm_cm_agent FOREIGN KEY (agent_id) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_cm_statut CHECK (statut IN ('EXCLU', 'A_ENVOYER', 'ENVOYE', 'DISTRIBUE', 'LU', 'ECHEC', 'ANNULE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('campagne.avant_echeance_jours_max', '5', 'Relance avant echeance : premier jour (J-n)', 'CAMPAGNE', UTC_TIMESTAMP()),
 ('campagne.avant_echeance_jours_min', '1', 'Relance avant echeance : dernier jour (J-n)', 'CAMPAGNE', UTC_TIMESTAMP());
