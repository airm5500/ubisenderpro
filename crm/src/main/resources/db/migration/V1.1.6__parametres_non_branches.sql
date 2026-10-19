-- Le nom de l'application et les couleurs de la charte ne sont pas encore appliques a l'interface :
-- caches de l'ecran Parametres tant qu'ils n'ont pas d'effet
UPDATE crm_parametre SET modifiable = FALSE WHERE cle IN ('app.nom', 'charte.couleur_principale', 'charte.couleur_secondaire');
