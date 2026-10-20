-- =====================================================================
-- UBIPHARM CRM - Centre de support, lot 2 (repris du centre de support de Prestige)
-- * tickets automatiques : une erreur inattendue ouvre un ticket rattache a son erreur (auteur NULL = le systeme)
-- * deux nouvelles origines au diagnostic : LENTEUR (requete du serveur au-dela du seuil)
--   et SURVEILLANCE (memoire, connexions a la base, disque, WhatsApp Web, arret brutal)
-- * experience utilisateur (RUM) : durees reelles des ecrans et des appels mesurees dans le navigateur,
--   agregees par tranche de 5 minutes ; aucune donnee metier, seulement l'adresse normalisee et des durees
-- =====================================================================

ALTER TABLE crm_support_ticket MODIFY auteur_id BIGINT NULL;
ALTER TABLE crm_support_message MODIFY auteur_id BIGINT NULL;

ALTER TABLE crm_support_erreur
    ADD COLUMN ticket_id BIGINT NULL AFTER traite_le,
    ADD CONSTRAINT fk_crm_support_erreur_ticket FOREIGN KEY (ticket_id) REFERENCES crm_support_ticket(id),
    DROP CONSTRAINT ck_crm_support_erreur_origine,
    ADD CONSTRAINT ck_crm_support_erreur_origine CHECK (origine IN ('SERVEUR', 'NAVIGATEUR', 'LENTEUR', 'SURVEILLANCE'));

-- Une ligne par tranche de 5 minutes, utilisateur, type (ECRAN ou API) et cle (titre de l'ecran ou appel normalise)
CREATE TABLE crm_support_rum (
    tranche DATETIME NOT NULL,
    utilisateur_id BIGINT NOT NULL,
    type_mesure VARCHAR(10) NOT NULL,
    cle VARCHAR(160) NOT NULL,
    nombre INT NOT NULL,
    total_ms BIGINT NOT NULL,
    max_ms INT NOT NULL,
    -- Mesures d'au moins 1 s et d'au moins 3 s : part des lenteurs ressenties
    nb_1s INT NOT NULL DEFAULT 0,
    nb_3s INT NOT NULL DEFAULT 0,
    echecs INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tranche, utilisateur_id, type_mesure, cle),
    KEY idx_crm_support_rum_type (type_mesure, tranche),
    CONSTRAINT ck_crm_support_rum_type CHECK (type_mesure IN ('ECRAN', 'API'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('support.ticket_auto', 'OUI', 'OUI_NON', NULL, NULL, TRUE,
  'Ouvrir un ticket automatiquement pour une erreur inattendue (dès la 1re survenue côté serveur)', 'SUPPORT', 4, UTC_TIMESTAMP()),
 ('support.ticket_auto_seuil_navigateur', '5', 'ENTIER', 1, 1000, TRUE,
  'Nombre de survenues d''une même erreur du navigateur à partir duquel un ticket est ouvert automatiquement', 'SUPPORT', 5, UTC_TIMESTAMP()),
 ('support.ticket_auto_max_jour', '10', 'ENTIER', 1, 1000, TRUE,
  'Nombre maximal de tickets ouverts automatiquement par jour (protection contre les rafales d''erreurs)', 'SUPPORT', 6, UTC_TIMESTAMP()),
 ('support.email', NULL, 'EMAIL', NULL, NULL, FALSE,
  'Adresse prévenue par e-mail de chaque ticket ouvert automatiquement (vide : aucun e-mail)', 'SUPPORT', 7, UTC_TIMESTAMP()),
 ('support.lenteur_ms', '3000', 'ENTIER', 1000, 600000, TRUE,
  'Durée (ms) d''une requête du serveur au-delà de laquelle elle est signalée comme lente au diagnostic', 'SUPPORT', 8, UTC_TIMESTAMP()),
 ('support.memoire_alerte_pct', '85', 'ENTIER', 50, 99, TRUE,
  'Mémoire du serveur utilisée (%) au-delà de laquelle une alerte est enregistrée (3 relevés consécutifs)', 'SUPPORT', 9, UTC_TIMESTAMP()),
 ('support.rum_actif', 'OUI', 'OUI_NON', NULL, NULL, TRUE,
  'Mesurer le temps d''ouverture des écrans et la durée des appels vécus par les utilisateurs (Expérience utilisateur)', 'SUPPORT', 10, UTC_TIMESTAMP()),
 ('support.rum_retention_jours', '14', 'ENTIER', 1, 90, TRUE,
  'Durée de conservation (jours) des mesures de l''expérience utilisateur', 'SUPPORT', 11, UTC_TIMESTAMP());
