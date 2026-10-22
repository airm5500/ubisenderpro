-- Le numero de l'agent s'affiche desormais lisible dans les messages (07 08 12 34 56) : utile surtout par SMS, ou le client l'appelle
UPDATE crm_variable_message SET exemple = '07 08 12 34 56' WHERE code = 'agent_numero' AND exemple = '2250700000001';
