-- =====================================================================
-- UBIPHARM CRM - Messages de disponibilite (retour en stock)
-- Un produit est disponible ou en rupture ; sa remise en stock est datee.
-- Une campagne promotionnelle peut citer des produits de retour en stock
-- (variable {produits_disponibles}, liste Excel jointe).
-- =====================================================================

ALTER TABLE crm_produit
    ADD COLUMN disponibilite VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE' AFTER prix,
    ADD COLUMN disponibilite_le DATETIME NULL AFTER disponibilite,
    ADD CONSTRAINT ck_crm_produit_disponibilite CHECK (disponibilite IN ('DISPONIBLE', 'RUPTURE'));

ALTER TABLE crm_campagne ADD COLUMN produit_ids VARCHAR(2000) NULL AFTER promotion_ids;

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('produits_disponibles', 'Produits de retour en stock (liste)', 'calcul.produits_disponibles',
  '• Doliprane 500 mg, boîte de 16', UTC_TIMESTAMP());

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at)
VALUES ('marketing.retour_stock_jours', '15', 'ENTIER', 1, 90, TRUE,
        'Un produit remis en stock reste « de retour » dans le catalogue pendant ce nombre de jours', 'MARKETING', 3, UTC_TIMESTAMP());
