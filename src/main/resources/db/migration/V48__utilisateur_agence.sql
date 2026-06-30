-- =====================================================================
-- UbiSenderPro - Cloisonnement multi-agences (module Recouvrement)
-- Agence de rattachement d'un utilisateur : un agent ne voit que le
-- portefeuille de son agence, sauf droit de vue consolidée (VOIR_GROUPE)
-- ou rôle ADMIN.
-- =====================================================================

ALTER TABLE usp_utilisateur ADD COLUMN agence VARCHAR(150) AFTER email;
