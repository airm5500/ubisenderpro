-- =====================================================================
-- UBIPHARM CRM - Campagnes promotionnelles du Marketing (WhatsApp, e-mail, SMS)
-- Une campagne Marketing vise une liste de diffusion avec un modele du Marketing.
-- Elle part du numero WhatsApp (ou de l'adresse, en reponse) de la personne qui
-- l'a preparee, apres validation par le directeur d'agence ou tout titulaire du
-- droit. Memes tables et meme chaine d'envoi que les relances : seul le module change.
-- =====================================================================

ALTER TABLE crm_modele_message
    ADD COLUMN module VARCHAR(20) NOT NULL DEFAULT 'RECOUVREMENT' AFTER id,
    DROP CONSTRAINT ck_crm_modele_message_type,
    ADD CONSTRAINT ck_crm_modele_message_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION')),
    ADD CONSTRAINT ck_crm_modele_message_module CHECK (module IN ('RECOUVREMENT', 'MARKETING'));

-- agences_cibles : agences des destinataires, calculees a la soumission ; un directeur d'agence
-- ne valide que les campagnes qui restent dans ses agences.
ALTER TABLE crm_campagne
    ADD COLUMN module VARCHAR(20) NOT NULL DEFAULT 'RECOUVREMENT' AFTER id,
    ADD COLUMN liste_id BIGINT AFTER modele_id,
    ADD COLUMN agences_cibles VARCHAR(500) AFTER perimetre_agents,
    DROP CONSTRAINT ck_crm_campagne_type,
    ADD CONSTRAINT ck_crm_campagne_type CHECK (type_campagne IN ('AVANT_ECHEANCE', 'JOUR_ECHEANCE', 'APRES_ECHEANCE', 'PERSONNALISEE', 'PROMOTION')),
    ADD CONSTRAINT ck_crm_campagne_module CHECK (module IN ('RECOUVREMENT', 'MARKETING')),
    ADD CONSTRAINT fk_crm_campagne_liste FOREIGN KEY (liste_id) REFERENCES crm_liste_diffusion (id),
    ADD KEY idx_crm_campagne_module (module, statut);

-- Un message Marketing ne porte sur aucune facture
ALTER TABLE crm_campagne_message MODIFY facture_id BIGINT NULL;

-- Expediteur des campagnes Marketing : la personne qui les prepare
INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('expediteur_nom',    'Nom de l''expéditeur',             'calcul.expediteur_nom',    'KONAN Marie',   UTC_TIMESTAMP()),
 ('expediteur_numero', 'Numéro WhatsApp de l''expéditeur', 'calcul.expediteur_numero', '07 00 00 00 09', UTC_TIMESTAMP()),
 ('segment',           'Segment du client',                'calcul.segment',           'Platinum',      UTC_TIMESTAMP());

INSERT INTO crm_droit (code, libelle, description, groupe, module_id, ordre)
SELECT d.code, d.libelle, d.description, d.groupe, m.id, d.ordre FROM (
    SELECT 'CAMPAGNES_MARKETING' code, 'Campagnes promotionnelles' libelle, 'Consulter les campagnes Marketing et leur suivi' description,
           'Campagnes' groupe, 110 ordre
    UNION ALL SELECT 'CAMPAGNES_MARKETING_PREPARER', 'Préparer des campagnes', 'Créer, modifier et soumettre ses campagnes à validation', 'Campagnes', 120
    UNION ALL SELECT 'CAMPAGNES_MARKETING_VALIDER', 'Valider les campagnes', 'Valider, rejeter, lancer et arrêter les campagnes de ses agences', 'Campagnes', 130
    UNION ALL SELECT 'MODELES_MARKETING', 'Modèles de messages', 'Créer, modifier et archiver les modèles promotionnels', 'Campagnes', 140
    UNION ALL SELECT 'NUMERO_MARKETING', 'Son numéro WhatsApp', 'Relier son numéro WhatsApp par QR code pour envoyer ses campagnes', 'Campagnes', 150
) d JOIN crm_module m ON m.code = 'MARKETING';

INSERT INTO crm_role_droit (role_id, droit_id, created_at)
SELECT r.id, d.id, UTC_TIMESTAMP() FROM crm_role r JOIN crm_droit d ON (
       r.code IN ('ADMIN', 'DG', 'DIR_COMMERCIAL', 'DIR_AGENCE')
           AND d.code IN ('CAMPAGNES_MARKETING', 'CAMPAGNES_MARKETING_PREPARER', 'CAMPAGNES_MARKETING_VALIDER', 'MODELES_MARKETING', 'NUMERO_MARKETING')
    OR r.code = 'CHARGE_MARKETING'
           AND d.code IN ('CAMPAGNES_MARKETING', 'CAMPAGNES_MARKETING_PREPARER', 'MODELES_MARKETING', 'NUMERO_MARKETING'));
