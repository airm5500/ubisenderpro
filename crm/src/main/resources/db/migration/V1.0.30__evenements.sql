-- =====================================================================
-- UBIPHARM CRM - Messages evenementiels (courtoisie, Marketing)
-- Calendrier des evenements : fetes, jours feries, fermetures d'agence,
-- conges. Un evenement annuel revient chaque annee a la meme date ; les
-- fetes a date variable (Paques, Tabaski, Ramadan...) se saisissent pour
-- l'annee. Un modele "Evenement" cite {evenement_nom}, {evenement_date}
-- et {evenement_fin} ; la campagne choisit l'evenement et peut se
-- repeter chaque annee.
-- =====================================================================

CREATE TABLE crm_evenement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    nature VARCHAR(20) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NULL,
    annuel BOOLEAN NOT NULL DEFAULT FALSE,
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    cree_par BIGINT NULL,
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_crm_evenement (statut, date_debut),
    CONSTRAINT fk_crm_evenement_cree FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT fk_crm_evenement_modifie FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id),
    CONSTRAINT ck_crm_evenement_nature CHECK (nature IN ('FETE', 'JOUR_FERIE', 'FERMETURE', 'AUTRE')),
    CONSTRAINT ck_crm_evenement_statut CHECK (statut IN ('ACTIF', 'ARCHIVE')),
    CONSTRAINT ck_crm_evenement_dates CHECK (date_fin IS NULL OR date_fin >= date_debut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Jours feries a date fixe en Cote d'Ivoire
INSERT INTO crm_evenement (nom, nature, date_debut, annuel, created_at) VALUES
 ('Jour de l''an', 'JOUR_FERIE', '2027-01-01', TRUE, UTC_TIMESTAMP()),
 ('Fête du Travail', 'JOUR_FERIE', '2027-05-01', TRUE, UTC_TIMESTAMP()),
 ('Fête de l''Indépendance', 'JOUR_FERIE', '2027-08-07', TRUE, UTC_TIMESTAMP()),
 ('Assomption', 'JOUR_FERIE', '2027-08-15', TRUE, UTC_TIMESTAMP()),
 ('Toussaint', 'JOUR_FERIE', '2026-11-01', TRUE, UTC_TIMESTAMP()),
 ('Journée nationale de la Paix', 'JOUR_FERIE', '2026-11-15', TRUE, UTC_TIMESTAMP()),
 ('Noël', 'FETE', '2026-12-25', TRUE, UTC_TIMESTAMP());

ALTER TABLE crm_campagne
    ADD COLUMN evenement_id BIGINT NULL AFTER produit_ids,
    ADD CONSTRAINT fk_crm_campagne_evenement FOREIGN KEY (evenement_id) REFERENCES crm_evenement (id),
    DROP CONSTRAINT ck_crm_campagne_type,
    ADD CONSTRAINT ck_crm_campagne_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION', 'EVENEMENT')),
    DROP CONSTRAINT ck_crm_campagne_recurrence,
    ADD CONSTRAINT ck_crm_campagne_recurrence CHECK (recurrence IN ('QUOTIDIENNE', 'HEBDOMADAIRE', 'MENSUELLE', 'ANNUELLE'));

ALTER TABLE crm_modele_message
    DROP CONSTRAINT ck_crm_modele_message_type,
    ADD CONSTRAINT ck_crm_modele_message_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION', 'EVENEMENT'));

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('evenement_nom',  'Nom de l''événement',              'calcul.evenement_nom',  'Noël',       UTC_TIMESTAMP()),
 ('evenement_date', 'Date de l''événement',             'calcul.evenement_date', '25/12/2026', UTC_TIMESTAMP()),
 ('evenement_fin',  'Dernier jour de l''événement',     'calcul.evenement_fin',  '26/12/2026', UTC_TIMESTAMP());

-- Modeles de depart, modifiables par le Marketing
INSERT INTO crm_modele_message (module, nom, type_campagne, canal, contenu, cree_par, created_at)
SELECT 'MARKETING', m.nom, 'EVENEMENT', 'WHATSAPP', m.contenu, u.id, UTC_TIMESTAMP() FROM (
    SELECT 'Vœux de fête' nom,
           'Bonjour {nom_pharmacie},\nà l''occasion de {evenement_nom}, toute l''équipe UBIPHARM {agence} vous adresse ses meilleurs vœux.\n{expediteur_nom}' contenu
    UNION ALL SELECT 'Fermeture de l''agence',
           'Bonjour {nom_pharmacie},\nl''agence UBIPHARM {agence} sera fermée du {evenement_date} au {evenement_fin} ({evenement_nom}). Pensez à passer vos commandes à l''avance.\n{expediteur_nom}'
) m JOIN crm_utilisateur u ON u.login = 'admin';

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('marketing.evenement_jours_avant', '0', 'ENTIER', 0, 30, TRUE,
  'Fête ou jour férié : envoi proposé ce nombre de jours avant l''événement (0 = le jour même)', 'MARKETING', 4, UTC_TIMESTAMP()),
 ('marketing.fermeture_jours_avant', '3', 'ENTIER', 0, 30, TRUE,
  'Annonce d''une fermeture d''agence : envoi proposé ce nombre de jours avant la fermeture', 'MARKETING', 5, UTC_TIMESTAMP()),
 ('marketing.evenement_heure', '8', 'ENTIER', 0, 23, TRUE,
  'Message d''événement : heure d''envoi proposée (heure d''Abidjan)', 'MARKETING', 6, UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'EVENEMENTS', 'Calendrier des événements', 'Saisir les fêtes, jours fériés et fermetures annoncés aux clients', 'Campagnes', m.id, 145
FROM crm_module m WHERE m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'EVENEMENTS'
WHERE r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE', 'CHARGE_MARKETING');
