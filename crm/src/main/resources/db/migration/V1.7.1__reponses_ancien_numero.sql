-- Reponses deja recues depuis l'ancien identifiant WhatsApp a 8 chiffres (225 + 8 chiffres, avant 2021) :
-- rattachees au numero a 10 chiffres connu du CRM quand un seul lui correspond, pour retrouver un seul fil.
UPDATE crm_message_recu r
JOIN (SELECT r2.id, MIN(n.numero) AS numero
      FROM crm_message_recu r2
      JOIN (SELECT numero_whatsapp AS numero FROM crm_contact WHERE statut = 'ACTIF'
            UNION SELECT numero_destinataire FROM crm_message_sortant
            UNION SELECT numero_destinataire FROM crm_campagne_message) n
        ON n.numero LIKE CONCAT('225__', SUBSTRING(r2.numero_client, 4))
      WHERE r2.numero_client REGEXP '^225[0-9]{8}$'
      GROUP BY r2.id
      HAVING COUNT(DISTINCT n.numero) = 1) x ON x.id = r.id
SET r.numero_client = x.numero,
    r.client_id = COALESCE(r.client_id, (SELECT ct.client_id FROM crm_contact ct
                                         WHERE ct.numero_whatsapp = x.numero AND ct.statut = 'ACTIF'
                                         ORDER BY ct.fonction = 'PRINCIPAL' DESC, ct.rang, ct.id LIMIT 1));
