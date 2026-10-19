-- =====================================================================
-- UBIPHARM CRM - Journal des actions (demande d'Airman du 19/10/2026)
-- « Qui a fait quoi » : un ecran global (Administration > Journal des actions)
-- reunit le journal des actions et celui des connexions, avec recherche
-- par periode, utilisateur, type de donnee, nature et texte libre.
-- Lecture seule : rien n'est modifie ni supprime dans les journaux.
-- =====================================================================

-- Historique d'une fiche (client, facture...) et filtre par utilisateur sur une periode
CREATE INDEX idx_crm_journal_entite ON crm_journal_action (entite, entite_id, created_at);
CREATE INDEX idx_crm_journal_utilisateur_date ON crm_journal_action (utilisateur_id, created_at);
CREATE INDEX idx_crm_jc_date ON crm_journal_connexion (created_at);

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
VALUES ('JOURNAL', 'Journal des actions', 'Consulter qui a créé, modifié, désactivé ou réactivé chaque donnée, et les connexions',
        'Administration', NULL, 308);

-- Administrateurs et directions en consultation globale
INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON d.code = 'JOURNAL'
WHERE r.code IN ('ADMIN', 'ADMINISTRATEUR_SANS_SMS', 'DG', 'DIR_COMMERCIAL');
