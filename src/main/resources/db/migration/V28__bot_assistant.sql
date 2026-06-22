-- =====================================================================
-- UbiSenderPro - Assistant (Bot) à règles : FAQ + état bot par conversation
-- Bot désactivé par défaut (bot.actif = false).
-- =====================================================================

-- État du bot par conversation : actif tant qu'aucun humain ne reprend la main.
ALTER TABLE usp_conversation
    ADD COLUMN bot_actif TINYINT(1) NOT NULL DEFAULT 1 AFTER statut;

-- Base de connaissance (questions/réponses déclenchées par mots-clés).
CREATE TABLE usp_bot_faq (
    id BIGINT NOT NULL AUTO_INCREMENT,
    declencheurs VARCHAR(1000) NOT NULL,
    reponse TEXT NOT NULL,
    ordre INT NOT NULL DEFAULT 0,
    actif TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Paramètres du bot (désactivé par défaut).
INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('bot.actif', 'false', 'Activer l''assistant automatique (bot)', 'BOT', NOW()),
 ('bot.message_transfert', 'Un instant, je vous mets en relation avec un conseiller.',
  'Message envoyé au client lors du passage à un humain', 'BOT', NOW()),
 ('bot.mots_cles_humain', 'conseiller,agent,humain,parler a quelqu''un,parler à quelqu''un,operateur,opérateur',
  'Mots-clés déclenchant le passage à un humain (séparés par des virgules)', 'BOT', NOW()),
 ('bot.mots_cles_escalade', 'reclamation,réclamation,remboursement,litige,plainte,urgent,urgence,avocat,scandale',
  'Mots-clés sensibles déclenchant une escalade vers un humain', 'BOT', NOW());

-- Quelques entrées FAQ d'exemple (modifiables / désactivables depuis l'UI).
INSERT INTO usp_bot_faq (declencheurs, reponse, ordre, actif, created_at) VALUES
 ('bonjour,bonsoir,salut,coucou', 'Bonjour 👋 ! Comment puis-je vous aider aujourd''hui ?', 1, 1, NOW()),
 ('horaire,horaires,ouvert,ouverture,heure', 'Nous sommes ouverts du lundi au samedi, de 8h à 19h.', 2, 1, NOW()),
 ('merci,super,parfait', 'Avec plaisir ! N''hésitez pas si vous avez d''autres questions. 🙂', 3, 1, NOW());
