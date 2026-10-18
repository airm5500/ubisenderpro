-- =====================================================================
-- UBIPHARM CRM - Ecran Parametres de l'administrateur
-- Chaque parametre porte son type et ses bornes : le serveur controle la
-- valeur saisie. Les parametres internes (non modifiables) restent caches.
-- =====================================================================

ALTER TABLE crm_parametre
    ADD COLUMN type_valeur VARCHAR(20) NOT NULL DEFAULT 'TEXTE' AFTER valeur,
    ADD COLUMN minimum INT AFTER type_valeur,
    ADD COLUMN maximum INT AFTER minimum,
    ADD COLUMN obligatoire BOOLEAN NOT NULL DEFAULT FALSE AFTER maximum,
    ADD COLUMN modifiable BOOLEAN NOT NULL DEFAULT TRUE AFTER obligatoire,
    ADD COLUMN ordre INT NOT NULL DEFAULT 0 AFTER categorie,
    ADD COLUMN modifie_par BIGINT AFTER ordre,
    ADD CONSTRAINT ck_crm_parametre_type CHECK (type_valeur IN ('TEXTE', 'ENTIER', 'HEURE', 'EMAIL', 'URL', 'COULEUR')),
    ADD CONSTRAINT fk_crm_parametre_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id);

UPDATE crm_parametre SET modifiable = FALSE WHERE cle = 'admin.mot_de_passe_initial';

-- Types, bornes, ordre d'affichage et libelles accentues
UPDATE crm_parametre SET ordre = 1, obligatoire = TRUE, description = 'Nom affiché de l''application' WHERE cle = 'app.nom';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'URL', description = 'Adresse HTTPS publique du CRM, utilisée pour les accusés de réception des SMS ; vide = statut demandé au fournisseur' WHERE cle = 'application.url_publique';
UPDATE crm_parametre SET ordre = 1, type_valeur = 'COULEUR', obligatoire = TRUE WHERE cle = 'charte.couleur_principale';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'COULEUR', obligatoire = TRUE WHERE cle = 'charte.couleur_secondaire';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'ENTIER', minimum = 1, maximum = 90, obligatoire = TRUE,
    description = 'Relance avant échéance : premier jour, en jours avant l''échéance (J-n)' WHERE cle = 'campagne.avant_echeance_jours_max';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'ENTIER', minimum = 1, maximum = 90, obligatoire = TRUE,
    description = 'Relance avant échéance : dernier jour, en jours avant l''échéance (J-n)' WHERE cle = 'campagne.avant_echeance_jours_min';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE,
    description = 'Heure (Abidjan) à partir de laquelle les relances partent' WHERE cle = 'whatsapp.envoi.heure_debut';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE,
    description = 'Heure (Abidjan) après laquelle plus aucune relance ne part' WHERE cle = 'whatsapp.envoi.heure_fin';
UPDATE crm_parametre SET ordre = 3, type_valeur = 'ENTIER', minimum = 5, maximum = 600, obligatoire = TRUE,
    description = 'Attente minimale entre deux messages d''un même numéro, en secondes' WHERE cle = 'whatsapp.envoi.delai_min_secondes';
UPDATE crm_parametre SET ordre = 4, type_valeur = 'ENTIER', minimum = 5, maximum = 600, obligatoire = TRUE,
    description = 'Attente maximale entre deux messages d''un même numéro, en secondes' WHERE cle = 'whatsapp.envoi.delai_max_secondes';
UPDATE crm_parametre SET ordre = 5, type_valeur = 'ENTIER', minimum = 1, maximum = 1000, obligatoire = TRUE,
    description = 'Messages de relance par numéro d''agent et par jour' WHERE cle = 'whatsapp.envoi.plafond_jour';
UPDATE crm_parametre SET ordre = 6, type_valeur = 'ENTIER', minimum = 1, maximum = 10, obligatoire = TRUE,
    description = 'Essais avant de passer un message en échec quand le service ou le serveur ne répond pas (WhatsApp, e-mail, SMS)' WHERE cle = 'whatsapp.envoi.tentatives_max';
UPDATE crm_parametre SET ordre = 7, obligatoire = TRUE, description = 'Préfixe pays par défaut (Côte d''Ivoire)' WHERE cle = 'whatsapp.prefixe_pays';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'EMAIL' WHERE cle = 'email.expediteur';
UPDATE crm_parametre SET ordre = 2 WHERE cle = 'email.nom_expediteur';
UPDATE crm_parametre SET ordre = 3, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE WHERE cle = 'email.envoi.heure_debut';
UPDATE crm_parametre SET ordre = 4, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE WHERE cle = 'email.envoi.heure_fin';
UPDATE crm_parametre SET ordre = 5, type_valeur = 'ENTIER', minimum = 0, maximum = 600, obligatoire = TRUE,
    description = 'Attente entre deux e-mails, en secondes' WHERE cle = 'email.envoi.delai_secondes';
UPDATE crm_parametre SET ordre = 6, type_valeur = 'ENTIER', minimum = 1, maximum = 100000, obligatoire = TRUE WHERE cle = 'email.envoi.plafond_jour';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE WHERE cle = 'sms.envoi.heure_debut';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'HEURE', minimum = 0, maximum = 24, obligatoire = TRUE WHERE cle = 'sms.envoi.heure_fin';
UPDATE crm_parametre SET ordre = 3, type_valeur = 'ENTIER', minimum = 0, maximum = 600, obligatoire = TRUE,
    description = 'Attente entre deux SMS, en secondes' WHERE cle = 'sms.envoi.delai_secondes';
UPDATE crm_parametre SET ordre = 4, type_valeur = 'ENTIER', minimum = 1, maximum = 100000, obligatoire = TRUE WHERE cle = 'sms.envoi.plafond_jour';
UPDATE crm_parametre SET ordre = 5, type_valeur = 'ENTIER', minimum = 0, maximum = 1440, obligatoire = TRUE,
    description = 'Attente, en minutes, avant un nouvel essai quand le fournisseur refuse la clé API' WHERE cle = 'sms.envoi.pause_cle_refusee_minutes';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'ENTIER', minimum = 3, maximum = 20, obligatoire = TRUE,
    description = 'Échecs de connexion avant verrouillage du compte' WHERE cle = 'securite.echecs_max';
UPDATE crm_parametre SET ordre = 2, type_valeur = 'ENTIER', minimum = 8, maximum = 64, obligatoire = TRUE WHERE cle = 'securite.mot_de_passe_longueur_min';

UPDATE crm_parametre SET ordre = 1, type_valeur = 'ENTIER', minimum = 100, maximum = 200000, obligatoire = TRUE,
    description = 'Nombre maximal de lignes par fichier importé' WHERE cle = 'import.lignes_max';
