-- =====================================================================
-- UBIPHARM CRM - Regles anti-blocage des numeros WhatsApp (reprises de Prestige, 24/10/2026) :
-- longue pause toutes les N messages, plafond par heure, montee en charge d'un numero recent,
-- pause de protection apres des echecs techniques en serie, doublon du jour refuse.
-- Le rythme de chaque numero est garde en base : un redemarrage du CRM ne le remet pas a zero.
-- =====================================================================

ALTER TABLE crm_session_whatsapp
    ADD COLUMN reprise_le DATETIME NULL COMMENT 'Prochain envoi autorise (UTC) : attente, longue pause ou pause de protection',
    ADD COLUMN reprise_motif VARCHAR(150) NULL,
    ADD COLUMN serie INT NOT NULL DEFAULT 0 COMMENT 'Messages envoyes depuis la derniere longue pause',
    ADD COLUMN echecs_consecutifs INT NOT NULL DEFAULT 0,
    ADD COLUMN montee_depuis DATE NULL COMMENT 'Montee en charge active depuis ce jour (null = plafond du jour complet)';

-- Doublon du jour : meme destinataire, meme texte
ALTER TABLE crm_campagne_message ADD KEY idx_crm_cm_doublon (numero_destinataire, envoye_le);
ALTER TABLE crm_message_sortant ADD KEY idx_crm_ms_doublon (numero_destinataire, envoye_le);

INSERT INTO crm_parametre (cle, valeur, type_valeur, minimum, maximum, obligatoire, description, categorie, ordre, created_at) VALUES
 ('whatsapp.envoi.plafond_heure', '40', 'ENTIER', 1, 500, TRUE,
  'Messages par numéro et par heure, tous envois confondus', 'WHATSAPP', 8, UTC_TIMESTAMP()),
 ('whatsapp.envoi.pause_apres_messages', '15', 'ENTIER', 0, 500, TRUE,
  'Longue pause après ce nombre de messages d''un même numéro (0 = jamais)', 'WHATSAPP', 9, UTC_TIMESTAMP()),
 ('whatsapp.envoi.pause_min_minutes', '5', 'ENTIER', 1, 120, TRUE,
  'Durée minimale de la longue pause, en minutes', 'WHATSAPP', 10, UTC_TIMESTAMP()),
 ('whatsapp.envoi.pause_max_minutes', '12', 'ENTIER', 1, 120, TRUE,
  'Durée maximale de la longue pause, en minutes', 'WHATSAPP', 11, UTC_TIMESTAMP()),
 ('whatsapp.envoi.montee_depart', '20', 'ENTIER', 1, 1000, TRUE,
  'Montée en charge d''un numéro récent : messages permis le premier jour', 'WHATSAPP', 12, UTC_TIMESTAMP()),
 ('whatsapp.envoi.montee_pas', '20', 'ENTIER', 0, 1000, TRUE,
  'Montée en charge : messages ajoutés chaque jour, jusqu''au plafond du jour (s''active numéro par numéro dans Numéros WhatsApp)', 'WHATSAPP', 13, UTC_TIMESTAMP()),
 ('whatsapp.envoi.echecs_avant_pause', '3', 'ENTIER', 0, 50, TRUE,
  'Échecs techniques de suite (service injoignable) avant de mettre le numéro au repos (0 = jamais)', 'WHATSAPP', 14, UTC_TIMESTAMP()),
 ('whatsapp.envoi.pause_echec_minutes', '30', 'ENTIER', 1, 720, TRUE,
  'Durée du repos du numéro après ces échecs, en minutes', 'WHATSAPP', 15, UTC_TIMESTAMP());

UPDATE crm_parametre SET description = 'Messages par numéro et par jour, tous envois confondus' WHERE cle = 'whatsapp.envoi.plafond_jour';
UPDATE crm_parametre SET description = 'Attente minimale entre deux messages d''un même numéro, en secondes (jamais moins de 5)'
    WHERE cle = 'whatsapp.envoi.delai_min_secondes';

-- Mots d'arret : desinscription et forme anglaise, en plus des mots existants
UPDATE crm_parametre SET valeur = CONCAT(valeur, ', DESINSCRIRE, DÉSINSCRIRE, UNSUBSCRIBE')
    WHERE cle = 'marketing.mots_desabonnement' AND UPPER(valeur) NOT LIKE '%UNSUBSCRIBE%';
