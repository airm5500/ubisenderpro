-- =====================================================================
-- Campagnes — V58 : colonnes trop courtes lors de la creation automatique
--
-- La validation d'une proposition d'envoi (Marketing) cree une campagne en
-- recopiant le message dans « description » et le titre dans « nom ». Or :
--   * description etait VARCHAR(500), alors qu'un message de promotion avec
--     une liste de produits depasse couramment cette taille -> l'insertion
--     echouait (« Data too long for column 'description' »), la transaction
--     etait annulee et la proposition ne pouvait pas etre validee ;
--   * nom etait VARCHAR(150) alors que le titre source (usp_envoi_propose.titre)
--     autorise 200 caracteres : meme debordement possible.
--
-- Les corps de message sont deja stockes en TEXT ailleurs dans le schema
-- (modeles, recouvrement, support) : on s'aligne sur cette convention.
-- Elargissement uniquement : aucune donnee existante n'est perdue.
-- =====================================================================

ALTER TABLE usp_campagne MODIFY COLUMN description TEXT;

ALTER TABLE usp_campagne MODIFY COLUMN nom VARCHAR(200) NOT NULL;
