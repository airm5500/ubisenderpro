-- =====================================================================
-- UBIPHARM CRM - Tableaux de bord
-- Chaque changement de solde d'une facture (saisie ou import) est garde :
-- il donne les montants recouvres, les paiements confirmes et le taux de
-- conversion des relances sur une periode.
-- =====================================================================

CREATE TABLE crm_facture_solde (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facture_id BIGINT NOT NULL,
    ancien_solde DECIMAL(14,2) NOT NULL,
    nouveau_solde DECIMAL(14,2) NOT NULL,
    import_id BIGINT NULL,
    cree_par BIGINT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crm_facture_solde_date (created_at, facture_id),
    KEY idx_crm_facture_solde_facture (facture_id, created_at),
    CONSTRAINT fk_crm_fso_facture FOREIGN KEY (facture_id) REFERENCES crm_facture(id),
    CONSTRAINT fk_crm_fso_import FOREIGN KEY (import_id) REFERENCES crm_import(id),
    CONSTRAINT fk_crm_fso_par FOREIGN KEY (cree_par) REFERENCES crm_utilisateur(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_crm_cm_envoye ON crm_campagne_message (envoye_le, statut);
CREATE INDEX idx_crm_mr_recu ON crm_message_recu (recu_le);

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('tableau_bord.echeance_proche_jours', '7', 'ENTIER', 1, 60, TRUE,
  'Tableau de bord : une facture arrive bientôt à échéance dans ce nombre de jours', 'CAMPAGNE', 5, UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT 'TABLEAU_BORD', 'Tableau de bord', 'Consulter les indicateurs du recouvrement de son périmètre', 'Relances', m.id, 1
FROM crm_module m WHERE m.code = 'RECOUVREMENT'
UNION ALL
SELECT 'TABLEAU_BORD_MARKETING', 'Tableau de bord', 'Consulter les indicateurs du marketing', 'Campagnes', m.id, 1
FROM crm_module m WHERE m.code = 'MARKETING';

-- Qui voit les campagnes d'un module voit son tableau de bord
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT DISTINCT rd.role_id, d.id, UTC_TIMESTAMP()
FROM crm_role_droit rd
JOIN crm_droit c ON c.id = rd.droit_id
JOIN crm_droit d ON (c.code = 'CAMPAGNES' AND d.code = 'TABLEAU_BORD') OR (c.code = 'CAMPAGNES_MARKETING' AND d.code = 'TABLEAU_BORD_MARKETING');
