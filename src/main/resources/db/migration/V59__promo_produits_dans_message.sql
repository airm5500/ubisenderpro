-- =====================================================================
-- Promotions — V59 : produits listes dans le message
--
-- Regle metier : quand une promotion ne porte que quelques produits, ils sont
-- listes directement dans le message (plus lisible et actionnable) ; au-dela
-- du seuil, le message renvoie vers le fichier Excel joint, comme avant.
--
-- Les gabarits livres sont mis a jour par l'application au demarrage, mais
-- UNIQUEMENT s'ils n'ont jamais ete personnalises (updated_at reste NULL tant
-- que personne ne les a edites) : aucun modele retouche par le client n'est
-- ecrase. Rien a faire ici pour les modeles.
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'promo.max_produits_message', '10',
       'Nombre maximal de produits listes dans le message d''une promotion ; au-dela, le message renvoie vers le fichier Excel joint',
       'MARKETING', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'promo.max_produits_message');
