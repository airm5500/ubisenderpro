-- =====================================================================
-- UBIPHARM CRM - Donnees de reference
-- =====================================================================

INSERT INTO crm_module (code, libelle) VALUES
 ('RECOUVREMENT', 'Recouvrement'),
 ('MARKETING',    'Marketing');

INSERT INTO crm_role (code, libelle, description, vision_globale, created_at) VALUES
 ('ADMIN',          'Administrateur',             'Parametres, comptes, sauvegardes',                 TRUE,  UTC_TIMESTAMP()),
 ('DIR_RECOUVREMENT','Direction du Recouvrement', 'Supervision globale du module Recouvrement',       TRUE,  UTC_TIMESTAMP()),
 ('DIR_AGENCE',     'Direction d''agence',        'Supervision des agents de son agence',             FALSE, UTC_TIMESTAMP()),
 ('AGENT',          'Agent',                      'Gestion de son portefeuille',                      FALSE, UTC_TIMESTAMP()),
 ('DG',             'Directeur General',          'Consultation des tableaux de bord',                TRUE,  UTC_TIMESTAMP()),
 ('DIR_COMMERCIAL', 'Directeur Commercial',       'Consultation des tableaux de bord',                TRUE,  UTC_TIMESTAMP());

INSERT INTO crm_agence (code, libelle, created_at) VALUES
 ('YOP', 'Yopougon', UTC_TIMESTAMP()),
 ('VRI', 'Vridi',    UTC_TIMESTAMP()),
 ('CEN', 'Centrale', UTC_TIMESTAMP()),
 ('BKE', 'Bouake',   UTC_TIMESTAMP()),
 ('GAG', 'Gagnoa',   UTC_TIMESTAMP());

-- Compte administrateur : le hash BCrypt est calcule au premier demarrage
-- a partir du parametre 'admin.mot_de_passe_initial' (voir InitialisationAdminServiceImpl).
INSERT INTO crm_utilisateur (login, nom_complet, mot_de_passe_hash, created_at) VALUES
 ('admin', 'Administrateur', 'INIT', UTC_TIMESTAMP());

INSERT INTO crm_habilitation (utilisateur_id, role_id, module_id, created_at)
SELECT u.id, r.id, NULL, UTC_TIMESTAMP() FROM crm_utilisateur u, crm_role r
WHERE u.login = 'admin' AND r.code = 'ADMIN';

INSERT INTO crm_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('app.nom',                    'UBIPHARM CRM', 'Nom affiche de l''application',                       'GENERAL',  UTC_TIMESTAMP()),
 ('charte.couleur_principale',  '#05253D',      'Couleur principale de la charte',                     'CHARTE',   UTC_TIMESTAMP()),
 ('charte.couleur_secondaire',  '#51D78E',      'Couleur secondaire de la charte',                     'CHARTE',   UTC_TIMESTAMP()),
 ('whatsapp.prefixe_pays',      '225',          'Prefixe pays par defaut (Cote d''Ivoire)',            'WHATSAPP', UTC_TIMESTAMP()),
 ('securite.echecs_max',        '5',            'Echecs de connexion avant verrouillage du compte',    'SECURITE', UTC_TIMESTAMP()),
 ('admin.mot_de_passe_initial', 'Admin@2026',   'Mot de passe initial de admin (hache au 1er demarrage puis efface)', 'SECURITE', UTC_TIMESTAMP());
