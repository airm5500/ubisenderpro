-- =====================================================================
-- UBIPHARM CRM - Couleurs de la charte modifiables dans les Parametres
-- Elles s'appliquent desormais a tout l'ecran (menu, titres, boutons).
-- =====================================================================

UPDATE crm_parametre SET modifiable = TRUE, description = 'Couleur principale (menu, titres, boutons)' WHERE cle = 'charte.couleur_principale';
UPDATE crm_parametre SET modifiable = TRUE, description = 'Couleur d''accent (onglet actif, boutons d''action)' WHERE cle = 'charte.couleur_secondaire';
