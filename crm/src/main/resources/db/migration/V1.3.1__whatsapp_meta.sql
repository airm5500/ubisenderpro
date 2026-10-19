-- =====================================================================
-- UBIPHARM CRM - API WhatsApp Cloud de Meta, en option par numero
-- WhatsApp Web (QR code, service wa-web) reste le mode par defaut. Un
-- numero passe a l'API officielle quand il est enregistre chez Meta :
-- hors de la fenetre de 24 h ouverte par une reponse du client, Meta
-- n'accepte qu'un modele approuve ; ses parametres {{1}}, {{2}}... sont
-- les variables du modele du CRM, dans leur ordre d'apparition.
-- =====================================================================

-- Compte Meta (une seule ligne) : les jetons ne se relisent jamais
CREATE TABLE crm_compte_meta (
    id BIGINT NOT NULL,
    jeton_acces VARCHAR(1000) NULL,
    secret_application VARCHAR(200) NULL,
    jeton_verification VARCHAR(100) NOT NULL,
    version_api VARCHAR(10) NOT NULL DEFAULT 'v21.0',
    modifie_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_crm_compte_meta_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO crm_compte_meta (id, jeton_verification, created_at) VALUES (1, LEFT(SHA2(CONCAT(UUID(), RAND()), 256), 32), UTC_TIMESTAMP());

ALTER TABLE crm_session_whatsapp
    ADD COLUMN fournisseur VARCHAR(20) NOT NULL DEFAULT 'WA_WEB' AFTER numero,
    ADD COLUMN meta_phone_number_id VARCHAR(50) NULL AFTER fournisseur,
    ADD UNIQUE KEY uk_crm_session_whatsapp_meta (meta_phone_number_id),
    ADD CONSTRAINT ck_crm_session_whatsapp_fournisseur CHECK (fournisseur IN ('WA_WEB', 'META'));

ALTER TABLE crm_modele_message
    ADD COLUMN meta_modele VARCHAR(512) NULL AFTER contenu,
    ADD COLUMN meta_langue VARCHAR(10) NULL AFTER meta_modele,
    ADD COLUMN meta_entete VARCHAR(20) NOT NULL DEFAULT 'AUCUN' AFTER meta_langue,
    ADD CONSTRAINT ck_crm_modele_message_meta_entete CHECK (meta_entete IN ('AUCUN', 'IMAGE', 'DOCUMENT'));

-- Valeurs des variables du message, une par ligne, pour les modeles Meta
ALTER TABLE crm_campagne_message ADD COLUMN parametres TEXT NULL AFTER contenu;

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'WHATSAPP_META', 'API WhatsApp officielle', 'Régler le compte Meta et passer un numéro de WhatsApp Web à l''API officielle', 'Échanges', m.id, 235
FROM crm_module m WHERE m.code = 'RECOUVREMENT';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'WHATSAPP_META' WHERE r.code = 'ADMIN';
