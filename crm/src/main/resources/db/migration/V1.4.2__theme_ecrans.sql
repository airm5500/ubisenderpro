-- =====================================================================
-- UBIPHARM CRM - Theme de la partie droite des ecrans (demande d'Airman du 19/10/2026)
-- A : panneaux a en-tete bleu et titre vert (par defaut), B : sobre, C : bandeau bleu en haut de page.
-- Couleurs supplementaires modifiables : fond de page et survol des lignes.
-- =====================================================================

INSERT INTO crm_parametre (cle, valeur, type_valeur, options, obligatoire, description, categorie, ordre, created_at) VALUES
 ('charte.theme', 'A', 'CHOIX',
  'A=A - Panneaux à en-tête bleu et titre vert|B=B - Sobre, en-tête de tableau bleu|C=C - Bandeau bleu en haut de page', TRUE,
  'Habillage de la partie droite des écrans (panneaux, survol et ligne sélectionnée)', 'CHARTE', 0, UTC_TIMESTAMP()),
 ('charte.couleur_fond', '#EEF1F4', 'COULEUR', NULL, TRUE,
  'Couleur du fond de page derrière les panneaux (thèmes A et C)', 'CHARTE', 3, UTC_TIMESTAMP()),
 ('charte.couleur_survol', '#E6F8EE', 'COULEUR', NULL, TRUE,
  'Couleur d''une ligne de tableau survolée par la souris', 'CHARTE', 4, UTC_TIMESTAMP());
