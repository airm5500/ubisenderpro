-- =====================================================================
-- UBIPHARM CRM - Module Marketing, lot 1 : listes de diffusion et
-- desabonnement (STOP). Une liste = des criteres sur la base clients
-- (facultatifs), plus des clients ajoutes a la main, moins des clients
-- exclus a la main. Elle se recalcule a chaque consultation.
-- =====================================================================

CREATE TABLE crm_liste_diffusion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    -- FALSE : seuls les clients ajoutes a la main
    par_criteres BOOLEAN NOT NULL DEFAULT TRUE,
    -- Criteres, chacun une liste de valeurs separees par des virgules ; vide = pas de filtre
    agence_ids VARCHAR(500),
    sous_agences VARCHAR(1000),
    villes VARCHAR(2000),
    segments VARCHAR(200),
    -- Numeros du client retenus pour l'envoi (fonctions des contacts, separees par des virgules)
    fonctions VARCHAR(200) NOT NULL DEFAULT 'PRINCIPAL',
    statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    cree_par BIGINT NOT NULL,
    modifie_par BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_liste_diffusion_nom (nom),
    CONSTRAINT ck_crm_liste_diffusion_statut CHECK (statut IN ('ACTIF', 'ARCHIVE')),
    CONSTRAINT fk_crm_liste_diffusion_cree_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id),
    CONSTRAINT fk_crm_liste_diffusion_modifie_par FOREIGN KEY (modifie_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ajouts et exclusions a la main ; une ligne retiree est desactivee, jamais supprimee
CREATE TABLE crm_liste_diffusion_client (
    id BIGINT NOT NULL AUTO_INCREMENT,
    liste_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    sens VARCHAR(10) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    cree_par BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_liste_diffusion_client (liste_id, client_id),
    KEY idx_crm_liste_diffusion_client_client (client_id),
    CONSTRAINT ck_crm_liste_diffusion_client_sens CHECK (sens IN ('AJOUT', 'EXCLUSION')),
    CONSTRAINT fk_crm_liste_diffusion_client_liste FOREIGN KEY (liste_id) REFERENCES crm_liste_diffusion (id),
    CONSTRAINT fk_crm_liste_diffusion_client_client FOREIGN KEY (client_id) REFERENCES crm_client (id),
    CONSTRAINT fk_crm_liste_diffusion_client_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Desabonnement du Marketing : les relances de recouvrement continuent
ALTER TABLE crm_client
    ADD COLUMN desabonne_marketing_le DATETIME AFTER commentaire,
    ADD COLUMN desabonne_marketing_motif VARCHAR(255) AFTER desabonne_marketing_le;

INSERT INTO crm_parametre (cle, valeur, type_valeur, obligatoire, description, categorie, ordre, created_at)
VALUES ('marketing.mots_desabonnement', 'STOP, ARRET, ARRÊT, DESABONNER, DÉSABONNER', 'TEXTE', TRUE,
        'Réponses WhatsApp qui désabonnent le client du Marketing (séparées par des virgules ; les relances continuent)', 'MARKETING', 1,
        UTC_TIMESTAMP());

-- Le profil qui prepare les envois Marketing, avec son propre numero WhatsApp
INSERT INTO crm_role (code, libelle, description, vision_globale, systeme, created_at)
VALUES ('CHARGE_MARKETING', 'Chargé marketing', 'Prépare les listes et les campagnes Marketing depuis son numéro WhatsApp', FALSE, TRUE,
        UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, 'Diffusion', m.id, d.ordre FROM (
    SELECT 'LISTES' code, 'Listes de diffusion' libelle, 'Consulter les listes et leurs destinataires' description, 210 ordre
    UNION ALL SELECT 'LISTES_GERER', 'Créer et modifier les listes', 'Critères, ajouts et exclusions, désabonnements', 220
) d JOIN crm_module m ON m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP()
FROM crm_role r JOIN crm_droit d ON d.code IN ('LISTES', 'LISTES_GERER')
WHERE r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE', 'CHARGE_MARKETING');
