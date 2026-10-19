-- =====================================================================
-- UBIPHARM CRM - Gestion des comptes par l'administrateur
-- Un compte cree ou reinitialise recoit un mot de passe provisoire, que
-- l'utilisateur doit remplacer a sa premiere connexion.
-- =====================================================================

ALTER TABLE crm_utilisateur
    ADD COLUMN mot_de_passe_provisoire BOOLEAN NOT NULL DEFAULT FALSE AFTER mot_de_passe_hash,
    -- Date du dernier changement fait par l'administrateur (droits, statut, mot de passe) :
    -- les sessions ouvertes avant doivent se reconnecter pour prendre les nouveaux droits
    ADD COLUMN droits_revises_le DATETIME(6) AFTER mot_de_passe_provisoire;

-- Le mot de passe initial de admin est connu (parametre d'installation) : a changer
UPDATE crm_utilisateur SET mot_de_passe_provisoire = TRUE WHERE login = 'admin' AND mot_de_passe_hash = 'INIT';

INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('securite.mot_de_passe_longueur_min', '10', 'Longueur minimale d''un mot de passe (lettres et chiffres obligatoires)', 'SECURITE', UTC_TIMESTAMP());

-- Libelles affiches dans l'ecran des habilitations
UPDATE crm_role SET libelle = 'Directeur Général', description = 'Consultation des tableaux de bord de tous les modules' WHERE code = 'DG';
UPDATE crm_role SET libelle = 'Direction d''agence', description = 'Supervision des agents de ses agences' WHERE code = 'DIR_AGENCE';
UPDATE crm_role SET description = 'Paramètres, comptes et habilitations' WHERE code = 'ADMIN';
UPDATE crm_role SET description = 'Supervision de toutes les agences, validation des campagnes' WHERE code = 'DIR_RECOUVREMENT';
UPDATE crm_role SET description = 'Gestion de son portefeuille et de ses campagnes' WHERE code = 'AGENT';
UPDATE crm_role SET description = 'Consultation des tableaux de bord' WHERE code = 'DIR_COMMERCIAL';

-- Libelles des variables de message, affiches a l'auteur des modeles
UPDATE crm_variable_message SET libelle = 'Numéro de compte' WHERE code = 'numero_compte' AND libelle = 'Numero de compte';
UPDATE crm_variable_message SET libelle = 'Référence de la facture' WHERE code = 'reference_facture' AND libelle = 'Reference de la facture';
UPDATE crm_variable_message SET libelle = 'Date d''échéance' WHERE code = 'date_echeance' AND libelle = 'Date d''echeance';
UPDATE crm_variable_message SET libelle = 'Solde restant dû' WHERE code = 'solde' AND libelle = 'Solde restant du';
UPDATE crm_variable_message SET libelle = 'Jours avant l''échéance' WHERE code = 'jours_avant_echeance' AND libelle = 'Jours avant l''echeance';
UPDATE crm_variable_message SET libelle = 'Numéro WhatsApp de l''agent' WHERE code = 'agent_numero' AND libelle = 'Numero WhatsApp de l''agent';

UPDATE crm_agence SET libelle = 'Bouaké' WHERE code = 'BKE' AND libelle = 'Bouake';
