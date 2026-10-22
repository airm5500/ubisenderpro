-- =====================================================================
-- UBIPHARM CRM - Roles parametrables : chaque role porte une liste de
-- droits (ecrans et actions) cochee par l'administrateur dans l'ecran
-- « Rôles et droits ». Un droit rattache a un module ne vaut que pour les
-- habilitations de ce module ; un droit sans module (administration)
-- vaut quel que soit le module de l'habilitation.
-- =====================================================================

ALTER TABLE crm_role
    ADD COLUMN systeme BOOLEAN NOT NULL DEFAULT FALSE AFTER actif,
    ADD COLUMN modifie_par BIGINT AFTER systeme,
    ADD COLUMN updated_at DATETIME AFTER created_at,
    ADD CONSTRAINT fk_crm_role_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id);

-- Roles livres avec l'application : renommables, jamais supprimes ; le role ADMIN garde la gestion des comptes et des roles
UPDATE crm_role SET systeme = TRUE;

CREATE TABLE crm_droit (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    libelle     VARCHAR(150) NOT NULL,
    description VARCHAR(300),
    -- Rubrique du menu, pour regrouper les cases a cocher
    groupe      VARCHAR(50)  NOT NULL,
    module_id   BIGINT,
    ordre       INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_droit_code (code),
    CONSTRAINT fk_crm_droit_module FOREIGN KEY (module_id) REFERENCES crm_module (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Case decochee : actif = false (rien n'est supprime, le journal garde qui a change quoi)
CREATE TABLE crm_role_droit (
    role_id    BIGINT   NOT NULL,
    droit_id   BIGINT   NOT NULL,
    actif      BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (role_id, droit_id),
    CONSTRAINT fk_crm_role_droit_role FOREIGN KEY (role_id) REFERENCES crm_role (id),
    CONSTRAINT fk_crm_role_droit_droit FOREIGN KEY (droit_id) REFERENCES crm_droit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, d.groupe, m.id, d.ordre FROM (
    SELECT 'MA_JOURNEE' code, 'Ma journée' libelle, 'Écran d''accueil de l''agent : ses relances du jour et ses réponses' description, 'Créances' groupe, 'RECOUVREMENT' module, 10 ordre
    UNION ALL SELECT 'FACTURES', 'Factures', 'Liste des factures et de leurs relances', 'Créances', 'RECOUVREMENT', 20
    UNION ALL SELECT 'CLIENTS', 'Clients', 'Liste des clients et fiches client', 'Créances', 'RECOUVREMENT', 30
    UNION ALL SELECT 'IMPORTS', 'Imports', 'Importer les clients (tous modules) et, en Recouvrement, les factures', 'Créances', NULL, 40
    UNION ALL SELECT 'PORTEFEUILLES', 'Portefeuilles agents', 'Voir les agents et leurs portefeuilles', 'Créances', 'RECOUVREMENT', 50
    UNION ALL SELECT 'PORTEFEUILLES_AFFECTER', 'Affecter et transférer des clients', 'Rattacher un client à un agent, transférer un portefeuille', 'Créances', 'RECOUVREMENT', 60
    UNION ALL SELECT 'INTERIMS', 'Intérims des agents', 'Confier le portefeuille d''un agent absent à un autre', 'Créances', 'RECOUVREMENT', 70
    UNION ALL SELECT 'PERIMETRE_AGENCES', 'Voir toute son agence', 'Clients et agents de ses agences ; sans ce droit, seulement son portefeuille', 'Créances', 'RECOUVREMENT', 80
    UNION ALL SELECT 'CAMPAGNES', 'Campagnes', 'Liste et suivi des campagnes de relance', 'Relances', 'RECOUVREMENT', 110
    UNION ALL SELECT 'CAMPAGNES_PREPARER', 'Préparer des campagnes', 'Créer, modifier et soumettre ses campagnes à validation', 'Relances', 'RECOUVREMENT', 120
    UNION ALL SELECT 'CAMPAGNES_VALIDER', 'Valider les campagnes', 'Valider, rejeter, lancer et arrêter les campagnes, y compris celles des autres', 'Relances', 'RECOUVREMENT', 130
    UNION ALL SELECT 'MODELES', 'Modèles de messages', 'Consulter les modèles de messages', 'Relances', 'RECOUVREMENT', 140
    UNION ALL SELECT 'MODELES_GERER', 'Écrire les modèles', 'Créer, modifier et archiver les modèles et les variables', 'Relances', 'RECOUVREMENT', 150
    UNION ALL SELECT 'REPONSES', 'Réponses des clients', 'Copie des réponses WhatsApp des clients', 'Échanges', 'RECOUVREMENT', 210
    UNION ALL SELECT 'NUMEROS', 'Son numéro WhatsApp', 'Relier son numéro professionnel par QR code', 'Échanges', 'RECOUVREMENT', 220
    UNION ALL SELECT 'NUMEROS_TOUS', 'Numéros WhatsApp des agents', 'Voir, relier et changer les numéros de tous les agents', 'Échanges', 'RECOUVREMENT', 230
    UNION ALL SELECT 'UTILISATEURS', 'Utilisateurs et habilitations', 'Créer les comptes et attribuer les rôles', 'Administration', NULL, 310
    UNION ALL SELECT 'ROLES', 'Rôles et droits', 'Créer les rôles et cocher leurs droits', 'Administration', NULL, 320
    UNION ALL SELECT 'PARAMETRES', 'Paramètres', 'Plages horaires, plafonds d''envoi et autres réglages', 'Administration', NULL, 330
    UNION ALL SELECT 'FOURNISSEURS_SMS', 'Fournisseurs SMS', 'Fournisseurs d''envoi des SMS et leur clé API', 'Administration', NULL, 340
) d LEFT JOIN crm_module m ON m.code = d.module;

-- Les clients servent a tous les modules : IMPORTS n'a pas de module, l'import des factures exige en plus le module Recouvrement

-- Droits de depart : ceux que chaque role avait jusqu'ici
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON (
       r.code = 'ADMIN' AND d.code <> 'MA_JOURNEE'
    OR r.code = 'DIR_RECOUVREMENT' AND (d.module_id IS NOT NULL OR d.code = 'IMPORTS') AND d.code <> 'MA_JOURNEE'
    OR r.code = 'DIR_AGENCE' AND d.code IN ('FACTURES', 'CLIENTS', 'IMPORTS', 'PORTEFEUILLES', 'PORTEFEUILLES_AFFECTER', 'PERIMETRE_AGENCES',
                                            'CAMPAGNES', 'CAMPAGNES_PREPARER', 'MODELES', 'REPONSES', 'NUMEROS')
    OR r.code = 'AGENT' AND d.code IN ('MA_JOURNEE', 'FACTURES', 'CLIENTS', 'CAMPAGNES', 'CAMPAGNES_PREPARER', 'MODELES', 'REPONSES', 'NUMEROS')
    OR r.code IN ('DG', 'DIR_COMMERCIAL') AND d.code IN ('FACTURES', 'CLIENTS', 'PORTEFEUILLES', 'PERIMETRE_AGENCES', 'CAMPAGNES', 'MODELES',
                                                       'REPONSES', 'NUMEROS'));
