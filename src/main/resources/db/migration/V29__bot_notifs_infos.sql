-- =====================================================================
-- UbiSenderPro - Bot : infos enrichies (adresse/horaires) + e-mail d'escalade
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at) VALUES
 ('bot.adresse', '', 'Adresse communiquée par le bot (question « où êtes-vous »)', 'BOT', NOW()),
 ('bot.horaires', 'Du lundi au samedi, de 8h à 19h.', 'Horaires communiqués par le bot', 'BOT', NOW()),
 ('bot.email_escalade', 'false', 'Notifier les superviseurs par e-mail lors d''une escalade', 'BOT', NOW()),
 ('mail.smtp.host', '', 'Serveur SMTP (notifications e-mail)', 'MAIL', NOW()),
 ('mail.smtp.port', '587', 'Port SMTP', 'MAIL', NOW()),
 ('mail.smtp.user', '', 'Utilisateur SMTP', 'MAIL', NOW()),
 ('mail.smtp.password', '', 'Mot de passe SMTP', 'MAIL', NOW()),
 ('mail.smtp.from', '', 'Adresse expéditeur des e-mails', 'MAIL', NOW()),
 ('mail.smtp.tls', 'true', 'Activer STARTTLS pour le SMTP', 'MAIL', NOW());
