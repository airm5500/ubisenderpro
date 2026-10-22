-- Un directeur d'agence voit-il seulement les discussions des agents de son module ? Desactive par defaut :
-- il voit celles de ses agences, quel que soit le module de l'agent (comportement d'origine).
INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('discussions.directeur_module_seul', 'NON', 'OUI_NON', NULL, TRUE,
  'Un directeur d''agence ne voit que les discussions des agents de son module (Recouvrement ou Marketing), plus les siennes', 'WHATSAPP', 24, UTC_TIMESTAMP());
