-- =====================================================================
-- UBIPHARM CRM - Nom de l'entreprise parametrable (remarque 18 d'Airman, decision Q4 du 22/10/2026)
-- Le nom n'est plus ecrit dans les modeles : la variable {nom_entreprise} le reprend du parametre de la Charte,
-- y compris dans les modeles deja modifies. Les messages deja produits ne changent pas (historique).
-- =====================================================================
INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('entreprise.nom', 'UBIPHARM CI', 'TEXTE', NULL, TRUE,
  'Nom de l''entreprise, affiché dans le menu et l''écran de connexion et cité dans les messages par {nom_entreprise}', 'CHARTE', -1, UTC_TIMESTAMP());

INSERT INTO crm_variable_message (code, libelle, source, exemple, created_at) VALUES
 ('nom_entreprise', 'Nom de l''entreprise', 'calcul.nom_entreprise', 'UBIPHARM CI', UTC_TIMESTAMP());

-- Le plus long d'abord, pour ne pas laisser « {nom_entreprise} CI » ; REPLACE distingue les majuscules
UPDATE crm_modele_message SET
  contenu = REPLACE(REPLACE(REPLACE(REPLACE(contenu, 'Ubipharm-Côte d''Ivoire', '{nom_entreprise}'), 'UBIPHARM CI', '{nom_entreprise}'),
            'UBIPHARM', '{nom_entreprise}'), 'Ubipharm', '{nom_entreprise}'),
  objet = REPLACE(REPLACE(REPLACE(REPLACE(objet, 'Ubipharm-Côte d''Ivoire', '{nom_entreprise}'), 'UBIPHARM CI', '{nom_entreprise}'),
            'UBIPHARM', '{nom_entreprise}'), 'Ubipharm', '{nom_entreprise}'),
  updated_at = UTC_TIMESTAMP()
WHERE contenu LIKE '%ubipharm%' OR objet LIKE '%ubipharm%';
