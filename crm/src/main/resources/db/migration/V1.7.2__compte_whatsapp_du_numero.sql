-- =====================================================================
-- UBIPHARM CRM - Compte WhatsApp exact de chaque numero ecrit (demande d'Airman du 21/10/2026)
-- A chaque envoi, WhatsApp indique le compte qui recoit le message : pour le 07 01 02 03 04 ouvert avant 2021,
-- c'est 225 01 02 03 04. La reponse venue de ce compte retrouve ainsi son fil avec certitude,
-- meme quand plusieurs numeros du CRM se terminent par les memes 8 chiffres.
-- =====================================================================

CREATE TABLE crm_compte_whatsapp (
    -- identifiant du compte tel que WhatsApp l'emploie (expediteur des reponses)
    compte_whatsapp VARCHAR(30) NOT NULL,
    -- numero ecrit par le CRM (225 + 10 chiffres)
    numero VARCHAR(30) NOT NULL,
    -- numero de l'agent dont l'envoi a appris la correspondance
    numero_expediteur VARCHAR(30) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (compte_whatsapp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
