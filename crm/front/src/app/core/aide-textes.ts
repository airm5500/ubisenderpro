/**
 * Textes d'origine de l'aide (maquette validee le 20/10/2026 : 1B bulles detaillees, 2B panneau « ? » de chaque ecran).
 * L'administrateur peut les reecrire dans Parametres > Aide et guidage ; seuls les textes reecrits sont en base.
 *
 * Bulles des boutons : cle « ecran|libelle » (ecran = chemin de la route, ex. « campagnes/:id ») ou « *|libelle » pour
 * tous les ecrans. Le libelle est celui du bouton (ou son aria-label), sans les nombres : « Appliquer à 3 client(s) »
 * devient « Appliquer à client(s) ». « grise » s'ajoute quand le bouton est inactif.
 *
 * Panneaux des ecrans : cle = chemin de la route. Dans le texte, une ligne « 1. … » ouvre une liste numerotee,
 * une ligne « - … » une liste a puces, une ligne vide separe deux paragraphes.
 */
export interface TexteAide {
  titre: string;
  texte: string;
  /** Explication ajoutee quand le bouton est grise. */
  grise?: string;
}

/** Libelle d'un bouton ramene a sa forme de cle : espaces reduits, nombres et compteurs retires. */
export function normaliserLibelle(libelle: string | null | undefined): string {
  return (libelle ?? '')
    .replace(/\(\s*\d+\s*\)/g, '')
    .replace(/\d+/g, '')
    .replace(/(…|\.\.\.)\s*$/, '')
    .replace(/\s+/g, ' ')
    .trim();
}

const b = (titre: string, texte: string, grise?: string): TexteAide => ({ titre, texte, grise });

export const AIDE_BOUTONS: Record<string, TexteAide> = {
  // ---------- Communs a tous les ecrans ----------
  '*|Annuler': b('Annuler', 'Ferme sans rien enregistrer. Les saisies en cours sont perdues.'),
  '*|Enregistrer': b('Enregistrer', 'Enregistre les changements. Ils prennent effet tout de suite et restent tracés dans le journal des actions.',
    "Rien n'a encore été modifié."),
  '*|Fermer': b('Fermer', 'Ferme la fenêtre.'),
  '*|Modifier': b('Modifier', "Ouvre la fiche pour la corriger. L'ancienne version reste visible dans le journal des actions."),
  '*|Archiver': b('Archiver', "Retire l'élément des listes de choix sans le supprimer : son historique est conservé et il peut être réactivé."),
  '*|Réactiver': b('Réactiver', "Remet l'élément archivé ou désactivé en service ; il revient dans les listes de choix."),
  '*|Désactiver': b('Désactiver', "Met l'élément hors service sans rien supprimer. Il peut être réactivé à tout moment."),
  '*|Dupliquer': b('Dupliquer', "Crée une copie modifiable, pratique pour repartir d'un élément existant."),
  '*|Rechercher': b('Rechercher', 'Applique la recherche et les filtres saisis à la liste.'),
  '*|Réinitialiser': b('Réinitialiser', 'Efface tous les filtres et revient à la liste complète.'),
  '*|Effacer': b('Effacer', 'Efface les filtres saisis.'),
  '*|Précédente': b('Page précédente', 'Affiche la page précédente de la liste.', 'Vous êtes déjà sur la première page.'),
  '*|Suivante': b('Page suivante', 'Affiche la page suivante de la liste.', 'Vous êtes déjà sur la dernière page.'),
  '*|Précédentes': b('Page précédente', 'Affiche les messages plus récents.', 'Vous êtes déjà sur la première page.'),
  '*|Suivantes': b('Page suivante', 'Affiche les messages plus anciens.', 'Vous êtes déjà sur la dernière page.'),
  '*|Précédent': b('Page précédente', 'Affiche la page précédente.', 'Vous êtes déjà sur la première page.'),
  '*|Suivant': b('Page suivante', 'Affiche la page suivante.', 'Vous êtes déjà sur la dernière page.'),
  '*|Actualiser': b('Actualiser', "Recharge les chiffres et la liste depuis le serveur, sans quitter l'écran."),
  '*|Exporter en CSV': b('Exporter en CSV', 'Télécharge la liste affichée, avec les filtres en cours, dans un fichier CSV ouvrable avec Excel.'),
  '*|Confirmer': b('Confirmer', "Exécute l'action décrite dans la fenêtre."),
  '*|Retour': b('Retour', "Revient à l'écran précédent."),
  '*|Retirer': b('Retirer', "Retire cet élément de la sélection en cours. Rien n'est supprimé."),
  '*|Vider la sélection': b('Vider la sélection', 'Décoche toutes les lignes sélectionnées.'),
  '*|Fiche client': b('Fiche client', 'Ouvre la fiche du client : coordonnées, factures, relances et échanges.'),
  '*|Se déconnecter': b('Se déconnecter', "Ferme votre session. Pensez-y sur un poste partagé."),
  '*|Mon mot de passe': b('Mon mot de passe', 'Remplacer votre mot de passe par un nouveau que vous êtes seul à connaître.'),
  '*|Envoyer maintenant': b('Envoyer maintenant', 'Le message part tout de suite, même hors de la plage horaire. Le rythme anti-blocage du numéro (attente, pauses, plafonds) reste respecté : s\'il n\'est pas encore permis, le CRM dit quand le numéro pourra reprendre.',
    'Ce message est déjà parti, annulé ou en échec : il ne peut plus être envoyé à la main.'),
  '*|Voir le message': b('Voir le message', 'Affiche le message tel que le client le reçoit, variables remplacées.'),
  '*|Importer': b('Importer', 'Charge un fichier Excel ou CSV. Une simulation montre ce qui sera créé ou modifié avant toute validation.'),
  '*|Exporter': b('Exporter', 'Télécharge la liste dans un fichier Excel.'),
  '*|Copier': b('Copier', 'Copie le texte dans le presse-papiers, prêt à être collé.'),
  '*|Choisir des clients': b('Choisir des clients', 'Ouvre le sélecteur : recherchez, filtrez, cochez les clients à ajouter.'),
  '*|Ajouter client(s)': b('Ajouter les clients cochés', 'Ajoute à la sélection les clients cochés dans le sélecteur.', 'Cochez au moins un client.'),
  '*|Nouvelle campagne': b('Nouvelle campagne', 'Prépare un envoi groupé : destinataires, modèle de message et date de départ, puis soumission à validation.'),
  '*|Soumettre à validation': b('Soumettre à validation', 'Envoie la campagne à la personne habilitée à la valider. Rien ne part avant sa validation.',
    'Complétez la campagne (destinataires, modèle, date) avant de la soumettre.'),
  '*|Renvoyer pour modification': b('Renvoyer pour modification', "Refuse la campagne en l'état et la rend à son auteur avec votre motif."),
  '*|Valider': b('Valider', "Autorise la campagne. Elle partira à la date prévue, pas avant."),
  '*|Lancer maintenant': b('Lancer maintenant', "Démarre tout de suite une campagne validée, sans attendre sa date prévue."),
  '*|Annuler la campagne': b('Annuler la campagne', "Arrête la campagne : les messages pas encore partis ne partiront pas. Ceux déjà envoyés restent dans l'historique."),
  '*|Vérifier les numéros': b('Vérifier les numéros', 'Contrôle que les numéros WhatsApp des clients choisis existent bien, avant un envoi.'),
  '*|Nouvelle facture': b('Nouvelle facture', "Saisir une facture qui n'est pas arrivée par l'import. Elle entre dans les relances selon son échéance."),
  '*|Nouvelle règle': b('Nouvelle règle', "Ajoute un délai de relance propre à une agence ou à un segment de clients."),
  '*|Modifier dans les paramètres': b('Délais par défaut', 'Les délais par défaut se règlent dans Paramètres > Campagnes.'),

  // ---------- Recouvrement ----------
  'factures|Importer': b('Importer des factures', "Charge le fichier de factures de la gestion commerciale. Une simulation montre les créations et mises à jour avant validation."),
  'factures|Relancer ces clients': b('Relancer ces clients', 'Prépare une campagne de relance pour les clients des factures cochées.'),
  'factures|Suivi de la facture': b('Suivi de la facture', "Historique de la facture : relances envoyées, promesses de paiement, réponses du client."),
  'factures|Lever et reprendre les relances': b('Lever et reprendre les relances', 'Lève la suspension : la facture reprend place dans les prochaines relances.'),
  'factures|Créer': b('Créer la facture', 'Enregistre la facture ; elle apparaît aussitôt dans la liste et sur la fiche du client.'),
  'clients|Nouveau client': b('Nouveau client', 'Crée la fiche d\'une pharmacie qui n\'est pas encore arrivée par l\'import.'),
  'clients|Importer des clients': b('Importer des clients', 'Charge le fichier clients de la gestion commerciale. Rien ne change avant votre validation.'),
  'clients|Mise à jour sélective': b('Mise à jour sélective', 'Change en une fois un champ (agence, segment, tournée…) sur tous les clients cochés.', 'Cochez au moins un client dans la liste.'),
  'clients|Vérifier des numéros': b('Vérifier des numéros', 'Contrôle que les numéros WhatsApp des clients cochés existent bien.'),
  'clients|Vérifier': b('Lancer la vérification', 'Interroge WhatsApp pour chaque numéro et affiche ceux qui ne sont pas joignables.'),
  'clients|Créer le client': b('Créer le client', 'Enregistre la fiche ; le client peut aussitôt recevoir factures et relances.'),
  'clients|Appliquer à client(s)': b('Appliquer', 'Enregistre la nouvelle valeur sur tous les clients cochés. Chaque changement est tracé dans le journal.'),
  'clients|Désactiver': b('Désactiver les clients', 'Les clients cochés ne reçoivent plus aucun message. Leur historique reste et ils peuvent être réactivés.'),
  'clients/:id|Relancer ce client': b('Relancer ce client', 'Prépare une campagne de relance pour les factures ouvertes de ce client.'),
  'clients/:id|Désactiver': b('Désactiver le client', "Le client ne reçoit plus aucun message. Son historique reste et il peut être réactivé."),
  'imports|Importer des clients': b('Importer des clients', "Ouvre l'assistant d'import pour un fichier de clients."),
  'imports|Importer des factures': b('Importer des factures', "Ouvre l'assistant d'import pour un fichier de factures."),
  'imports|Reprendre l\'import': b("Reprendre l'import", "Rouvre l'import resté en cours pour le corriger ou le valider."),
  'imports|Télécharger les rejets (CSV)': b('Télécharger les rejets', 'Télécharge les lignes refusées avec la raison du refus, pour les corriger dans le fichier.'),
  'imports/nouveau|Télécharger un fichier exemple': b('Fichier exemple', 'Télécharge un fichier modèle avec les colonnes attendues.'),
  '*|Télécharger un fichier exemple': b('Fichier exemple', 'Télécharge un fichier modèle avec les colonnes attendues.'),
  '*|Enregistrer ce modèle': b('Enregistrer ce modèle', 'Mémorise la correspondance des colonnes pour les prochains fichiers de même forme.'),
  '*|Changer de fichier': b('Changer de fichier', 'Revient au choix du fichier ; rien n\'a encore été importé.'),
  '*|Lancer la simulation': b('Lancer la simulation', "Lit tout le fichier et montre ce qui serait créé, modifié ou refusé. Rien n'est encore enregistré.",
    'Associez d\'abord les colonnes obligatoires.'),
  '*|Simulation': b('Simulation en cours', 'Le fichier est en cours de lecture.'),
  '*|Télécharger le rapport d\'erreurs': b("Rapport d'erreurs", 'Télécharge les lignes refusées avec la raison, pour les corriger.'),
  '*|Corriger la ligne': b('Corriger la ligne', 'Corrige cette ligne directement ici, sans retoucher le fichier.'),
  '*|Corriger et réévaluer': b('Corriger et réévaluer', 'Enregistre la correction et refait le contrôle de la ligne.'),
  '*|Abandonner': b('Abandonner', "Abandonne cet import : rien n'est enregistré."),
  '*|Valider l\'import': b("Valider l'import", 'Enregistre les créations et mises à jour montrées par la simulation. Rien n\'est jamais supprimé.',
    'Lancez d\'abord la simulation.'),
  '*|Import': b('Import en cours', "L'import est en cours d'enregistrement."),
  '*|Importer un autre fichier': b('Importer un autre fichier', "Recommence l'assistant avec un nouveau fichier."),
  'portefeuilles|Affecter à un agent': b('Affecter à un agent', "Confie les clients cochés à un agent. L'ancienne affectation reste dans l'historique.", 'Cochez au moins un client.'),
  'portefeuilles|Retirer de leur agent': b('Retirer de leur agent', "Les clients cochés n'ont plus d'agent : ils ne sont plus relancés au nom de personne.", 'Cochez au moins un client.'),
  'portefeuilles|Organiser un intérim': b('Organiser un intérim', "Pendant une absence, un autre agent suit le portefeuille ; tout revient à la date de fin."),
  'portefeuilles|Transférer le portefeuille': b('Transférer le portefeuille', "Confie définitivement tous les clients d'un agent à un autre."),
  'portefeuilles|Annuler l\'intérim': b("Annuler l'intérim", "Met fin tout de suite à l'intérim : l'agent titulaire reprend ses clients."),
  'portefeuilles|Numéro d\'envoi': b("Numéro d'envoi", 'Numéro WhatsApp depuis lequel partent les relances de ce portefeuille.'),
  'campagnes|Renvoyer': b('Renvoyer pour modification', "Rend la campagne à son auteur avec votre motif."),
  '*|Calculer le récapitulatif': b('Calculer le récapitulatif', 'Compte les clients, factures et montants visés par vos critères avant de soumettre.'),
  '*|Recalculer': b('Recalculer', 'Refait le récapitulatif avec les critères modifiés.'),
  '*|Enregistrer le brouillon': b('Enregistrer le brouillon', 'Garde la campagne sans la soumettre ; vous pourrez la reprendre plus tard.'),
  '*|Retirer les clients choisis': b('Retirer les clients choisis', 'Écarte de la campagne les clients cochés dans le récapitulatif.'),
  'modeles|Nouveau modèle': b('Nouveau modèle', 'Écrit un message type avec ses variables, réutilisable dans les campagnes et les envois.'),
  'modeles|Nouvelle variable': b('Nouvelle variable', 'Ajoute une variable {code} que les modèles peuvent citer.'),
  '*|Exporter en Word': b('Exporter en Word', 'Télécharge le modèle en document Word pour le relire ou le faire valider.'),
  'marketing/modeles|Nouveau modèle': b('Nouveau modèle promotionnel', 'Écrit un message type pour les campagnes marketing, avec ses variables et son affiche.'),

  // ---------- Echanges ----------
  'discussions|Nouvelle discussion': b('Nouvelle discussion', 'Écrire à un client qui ne vous a pas encore écrit, depuis votre numéro WhatsApp.'),
  'discussions|Envoyer': b('Envoyer', 'Envoie le message au client depuis votre numéro WhatsApp.', 'Écrivez un message ou joignez un fichier.'),
  'envoi-masse|Nouvel envoi': b('Nouvel envoi', 'Prépare un même message pour plusieurs clients ou numéros.'),
  'envoi-masse|Vérifier les destinataires': b('Vérifier les destinataires', 'Contrôle les numéros et écarte les doublons et les clients désabonnés avant l\'envoi.'),
  'envoi-masse|Envoyer à destinataire(s)': b('Envoyer', 'Les messages partent depuis votre numéro, au rythme réglé pour éviter le blocage du numéro.',
    'Vérifiez d\'abord les destinataires.'),
  'envoi-masse|Programmer à destinataire(s)': b('Programmer', "Les messages partiront à la date et à l'heure choisies, depuis votre numéro."),
  'envoi-masse|Réintégrer les lignes corrigées': b('Réintégrer les lignes corrigées', 'Remet dans l\'envoi les numéros que vous venez de corriger.'),
  'envoi-masse|Les ignorer': b('Les ignorer', 'Laisse de côté les numéros invalides et poursuit avec les autres.'),
  'envoi-masse|Renvoyer les échec(s)': b('Renvoyer les échecs', 'Retente les messages qui ne sont pas partis.'),
  'envoi-masse|Annuler l\'envoi': b("Annuler l'envoi", "Arrête l'envoi : les messages pas encore partis ne partiront pas."),
  'numeros|Scanner le QR code': b('Scanner le QR code', 'Affiche un QR code à scanner avec le téléphone (WhatsApp > Appareils connectés) pour relier le numéro.'),
  'numeros|Ou scanner le QR code (WhatsApp Web)': b('Relier par WhatsApp Web', 'Affiche un QR code à scanner avec le téléphone. Le téléphone doit rester allumé et connecté.'),
  'numeros|Mettre en pause': b('Mettre en pause', 'Suspend les envois depuis ce numéro ; les messages attendent la reprise.'),
  'numeros|Vérifier et reprendre': b('Vérifier et reprendre', 'Contrôle que le numéro répond, puis reprend les envois en attente.'),
  'numeros|Délier le téléphone': b('Délier le téléphone', "Déconnecte le téléphone : plus aucun message ne part de ce numéro jusqu'à un nouveau QR code."),
  'numeros|Relier à l\'API officielle': b("Relier à l'API officielle", 'Utilise le compte Meta (WhatsApp Business) du numéro au lieu de WhatsApp Web.'),
  'numeros|Passer à l\'API officielle': b("Passer à l'API officielle", 'Bascule ce numéro sur le compte Meta enregistré.'),
  'numeros|Revenir à WhatsApp Web': b('Revenir à WhatsApp Web', "Abandonne l'API officielle pour ce numéro et repasse par le téléphone relié."),
  'numeros|Enregistrer le compte Meta': b('Enregistrer le compte Meta', 'Enregistre les identifiants du compte WhatsApp Business fournis par Meta.'),
  'numeros|Activer la montée en charge (numéro neuf)': b('Activer la montée en charge', "Pour un numéro neuf : il n'envoie que quelques messages le premier jour (20 par défaut), puis davantage chaque jour jusqu'au plafond du jour. C'est la meilleure protection contre le blocage d'un numéro récent."),
  'numeros|Couper la montée en charge': b('Couper la montée en charge', 'Le numéro retrouve tout de suite son plafond du jour complet.'),
  'numeros|Changer le numéro d\'un agent': b("Changer le numéro d'un agent", 'Remplace le numéro professionnel d\'un agent ; l\'historique des échanges est conservé.'),
  'historique-envois|Exporter en CSV': b('Exporter en CSV', 'Télécharge les messages affichés, avec leur statut, dans un fichier CSV.'),

  // ---------- Marketing ----------
  'marketing/campagnes|Enregistrer en brouillon': b('Enregistrer en brouillon', 'Garde la campagne sans la soumettre ; vous la reprendrez plus tard.'),
  'marketing/campagnes|Programmer': b('Programmer', 'Soumet la campagne à validation ; une fois validée, elle part à la date et à l\'heure choisies.'),
  'marketing/campagnes|Envoyer dès la création': b('Envoyer dès la création', 'Soumet la campagne à validation ; elle part dès qu\'elle est validée.'),
  'marketing/listes|Nouvelle liste': b('Nouvelle liste', 'Crée une liste de clients par critères (agence, segment, ville…), à corriger client par client.'),
  'marketing/listes|Voir la liste': b('Voir la liste', 'Ouvre la liste pour voir ses clients et la corriger.'),
  'marketing/listes|Créer la liste': b('Créer la liste', 'Enregistre la liste ; elle est aussitôt utilisable dans une campagne.'),
  'marketing/listes/:id|Modifier les critères': b('Modifier les critères', 'Change les critères de la liste ; les clients ajoutés ou exclus à la main sont gardés.'),
  'marketing/listes/:id|Exclure de la liste': b('Exclure de la liste', 'Écarte ce client de la liste même s\'il répond aux critères.'),
  'marketing/listes/:id|Retirer de la liste': b('Retirer de la liste', 'Retire ce client ajouté à la main.'),
  'marketing/listes/:id|Réintégrer dans la liste': b('Réintégrer dans la liste', 'Remet dans la liste un client exclu.'),
  'marketing/listes/:id|Désabonner': b('Désabonner', 'Le client ne reçoit plus aucun message marketing, comme s\'il avait répondu STOP.'),
  'marketing/listes/:id|Réabonner': b('Réabonner', 'Le client recevra de nouveau les messages marketing. À faire seulement à sa demande.'),
  'marketing/catalogue|Importer des produits': b('Importer des produits', 'Charge le fichier Excel des produits ; une simulation montre les changements avant validation.'),
  'marketing/catalogue|Nouveau produit': b('Nouveau produit', 'Ajoute un produit au catalogue, à citer ensuite dans les promotions.'),
  'marketing/catalogue|Ajouter le produit': b('Ajouter le produit', 'Enregistre le produit dans le catalogue.'),
  'marketing/catalogue|Marquer en rupture': b('Marquer en rupture', 'Signale le produit indisponible ; il sort des promotions annoncées.'),
  'marketing/catalogue|Marquer de retour en stock': b('Marquer de retour en stock', 'Le produit est de nouveau disponible ; vous pourrez annoncer son retour.'),
  'marketing/catalogue|Annoncer leur retour aux clients': b('Annoncer leur retour', 'Prépare une campagne qui annonce aux clients le retour en stock des produits cochés.'),
  'marketing/promotions|Nouvelle promotion': b('Nouvelle promotion', 'Crée une période de promotion, puis ajoutez-y les produits et leur avantage.'),
  'marketing/promotions|Importer des produits en promotion': b('Importer des produits en promotion', 'Charge un fichier Excel des produits et de leur avantage pour une promotion.'),
  'marketing/promotions|Créer la promotion': b('Créer la promotion', 'Enregistre la promotion ; ajoutez ensuite ses produits.'),
  'marketing/promotions/:id|Ajouter un produit': b('Ajouter un produit', 'Ajoute un produit du catalogue avec son avantage (unités gratuites ou prix promotionnel).'),
  'marketing/promotions/:id|Ajouter à la promotion': b('Ajouter à la promotion', 'Enregistre le produit et son avantage dans la promotion.'),
  'marketing/promotions/:id|Retirer de la promotion': b('Retirer de la promotion', 'Le produit n\'est plus cité par la promotion ; il reste au catalogue.'),
  'marketing/promotions/:id|Remettre dans la promotion': b('Remettre dans la promotion', 'Le produit retiré redevient cité par la promotion.'),
  'marketing/promotions/:id|Activer': b('Activer', 'La promotion devient active tout de suite et peut être citée par une campagne.'),
  'marketing/promotions/:id|Programmer': b('Programmer', 'La promotion deviendra active d\'elle-même à sa date de début.'),
  'marketing/promotions/:id|Désactiver': b('Désactiver', 'Arrête la promotion avant sa date de fin.'),
  'marketing/informations|Nouvelle information': b('Nouvelle information', 'Saisit une information pour les clients : livraison, garde, fermeture, alerte produit…'),
  'marketing/informations|Annoncer': b('Annoncer', "Prépare la campagne qui envoie cette information aux clients concernés, à la date et à l'heure voulues."),
  'marketing/evenements|Nouvel événement': b('Nouvel événement', 'Ajoute une fête, un jour férié ou une fermeture à annoncer aux clients.'),
  'marketing/tableau-de-bord|Campagnes': b('Campagnes', 'Ouvre la liste des campagnes marketing.'),

  // ---------- Administration ----------
  'utilisateurs|Nouveau compte': b('Nouveau compte', 'Crée un utilisateur avec un rôle par module. Un mot de passe provisoire lui est donné.'),
  'utilisateurs|Créer le compte': b('Créer le compte', 'Enregistre le compte et affiche le mot de passe provisoire à transmettre.'),
  'utilisateurs|Modifier l\'utilisateur': b("Modifier l'utilisateur", 'Change le nom, les agences ou les rôles du compte.'),
  'utilisateurs|Nouveau mot de passe': b('Nouveau mot de passe', 'Donne un mot de passe provisoire, à remplacer par l\'utilisateur à sa prochaine connexion.'),
  'utilisateurs|Désactiver l\'utilisateur': b("Désactiver l'utilisateur", 'Le compte ne peut plus se connecter ; sa session en cours est fermée. Son historique reste.'),
  'utilisateurs|Réactiver l\'utilisateur': b("Réactiver l'utilisateur", 'Le compte peut de nouveau se connecter.'),
  'utilisateurs|Historique des actions': b('Historique des actions', 'Ouvre le journal des actions de cet utilisateur.'),
  'utilisateurs|Connexions': b('Connexions', 'Affiche les connexions récentes (date, poste, réussite ou échec).'),
  'utilisateurs|Journal des actions': b('Journal des actions', 'Ouvre le journal de tout ce qui a été créé ou modifié.'),
  'utilisateurs|Retirer ce rôle': b('Retirer ce rôle', 'Retire le rôle du compte pour ce module.'),
  'utilisateurs|Ajouter un rôle sur un autre module': b('Ajouter un rôle', 'Donne au compte un rôle sur l\'autre module (Recouvrement ou Marketing).'),
  'utilisateurs|C\'est noté': b("C'est noté", 'Ferme la fenêtre une fois le mot de passe provisoire transmis.'),
  'roles|Nouveau rôle': b('Nouveau rôle', 'Crée un rôle et cochez les écrans et actions qu\'il ouvre.'),
  'roles|Créer le rôle': b('Créer le rôle', 'Enregistre le rôle ; il s\'attribue ensuite dans Utilisateurs et habilitations.'),
  'roles|Modifier le rôle': b('Modifier le rôle', 'Change les droits cochés. Les utilisateurs concernés les reçoivent à leur prochaine action.'),
  'roles|Désactiver le rôle': b('Désactiver le rôle', 'Le rôle ne s\'attribue plus. Il n\'est jamais supprimé.'),
  'roles|Réactiver le rôle': b('Réactiver le rôle', 'Le rôle s\'attribue de nouveau.'),
  'journal|Imprimer': b('Imprimer', 'Imprime le journal affiché avec ses filtres.'),
  'journal|Excel': b('Exporter en Excel', 'Télécharge le journal affiché dans un fichier Excel.'),
  'journal|CSV': b('Exporter en CSV', 'Télécharge le journal affiché dans un fichier CSV.'),
  "journal|Aujourd'hui": b("Aujourd'hui", 'Limite le journal aux actions du jour.'),
  'journal|derniers jours': b('Période', 'Limite le journal aux derniers jours.'),
  'journal|Retirer le filtre client': b('Retirer le filtre client', 'Affiche de nouveau les actions sur tous les clients.'),
  'parametres|Enregistrer': b('Enregistrer', 'Enregistre les réglages modifiés ; ils prennent effet tout de suite et sont tracés dans le journal.',
    "Aucun réglage n'a été modifié."),
  'parametres|Annuler': b('Annuler', 'Remet les valeurs enregistrées.', "Aucun réglage n'a été modifié."),
  'parametres|Choisir un logo': b('Choisir un logo', 'Remplace le logo du menu et des documents (PNG, JPEG ou SVG).'),
  '*|Nouvelle agence': b('Nouvelle agence', 'Ajoute une agence du réseau, avec son code d\'import.'),
  '*|Nouveau fournisseur': b('Nouveau fournisseur', 'Décrit l\'API d\'un fournisseur SMS ; aucun développement n\'est nécessaire.'),
  '*|Utiliser par défaut': b('Utiliser par défaut', 'Les SMS partiront désormais par ce fournisseur.'),
  '*|Copier l\'adresse': b("Copier l'adresse", "Copie l'adresse de retour à donner au fournisseur."),
  '*|Envoyer l\'essai': b("Envoyer l'essai", 'Envoie un SMS de test au numéro saisi pour vérifier le réglage.'),
  'support|Nouveau ticket': b('Nouveau ticket', 'Pose une question ou signale un problème à l\'équipe support.'),
  'support|Ouvrir le ticket': b('Ouvrir le ticket', 'Envoie votre demande au support ; suivez la réponse dans Mes tickets.'),
  'support|Ouvrir un ticket': b('Ouvrir un ticket', 'Ouvre un ticket au support pour cette erreur.'),
  'support|Relancer les sondes': b('Relancer les sondes', 'Revérifie la base, le disque, WhatsApp et les envois.'),
  'support|Marquer comme traitée': b('Marquer comme traitée', "L'erreur sort de la liste à traiter ; elle revient si elle se reproduit."),
  'support|Marquer traitée': b('Marquer traitée', "L'erreur sort de la liste à traiter ; elle revient si elle se reproduit."),
  'support|Nouvelle question': b('Nouvelle question', 'Ajoute une question et sa réponse à la FAQ.'),
  'support|Envoyer': b('Envoyer', 'Ajoute votre message à la conversation du ticket.'),
  'mot-de-passe|Enregistrer le mot de passe': b('Enregistrer le mot de passe', 'Remplace votre mot de passe. Il servira dès la prochaine connexion.'),
  'connexion|Se connecter': b('Se connecter', 'Ouvre votre session avec votre identifiant et votre mot de passe.')
};

const p = (titre: string, texte: string): TexteAide => ({ titre, texte });

export const AIDE_PAGES: Record<string, TexteAide> = {
  'ma-journee': p('Ma journée', `Ce que vous avez à faire aujourd'hui sur votre portefeuille : factures en retard, promesses à surveiller, réponses des clients.

1. Commencez par les actions proposées en haut de l'écran.
2. Ouvrez la fiche d'un client pour voir ses factures et ses échanges.
3. « Nouvelle campagne » prépare les relances du jour, envoyées depuis votre numéro après validation.`),
  'tableau-de-bord': p('Tableau de bord', `Les chiffres du recouvrement sur votre périmètre : montants dus, retards, relances envoyées et encaissements.

- Un agent voit son portefeuille, un directeur ses agences, la direction tout le réseau.
- « Actualiser » recharge les chiffres.`),
  'factures': p('Factures et créances', `Chaque facture se suit à part, avec sa propre échéance. Les relances partent selon les délais de relance.

1. Filtrez par statut, agence ou retard, puis « Rechercher ».
2. Cochez des factures et « Relancer ces clients » pour préparer une campagne.
3. L'icône de suivi ouvre l'historique d'une facture : relances, promesses de paiement, réponses.`),
  'clients': p('Clients', `Les pharmacies de votre périmètre, avec leur agent de recouvrement et leur solde dû.

- Cochez des clients pour une mise à jour sélective, une vérification des numéros ou une désactivation.
- Un client désactivé ne reçoit plus aucun message ; il n'est jamais supprimé.
- Les clients arrivent surtout par l'import du fichier de la gestion commerciale.`),
  'clients/:id': p('Fiche client', `Tout sur un client : coordonnées, numéros WhatsApp, agent, factures, relances, échanges et historique.

- « Relancer ce client » prépare une campagne pour ses factures ouvertes.
- « Vérifier les numéros » contrôle que ses numéros WhatsApp existent.`),
  'imports': p('Imports', `L'historique des fichiers importés. Chaque import passe par une simulation : rien n'est modifié avant votre validation, et rien n'est jamais supprimé.

- « Reprendre l'import » rouvre un import resté en cours.
- Les lignes rejetées se téléchargent pour être corrigées dans le fichier.`),
  'imports/nouveau': p("Assistant d'import", `Un fichier Excel ou CSV en quatre étapes.

1. Choisissez le fichier (un fichier exemple montre les colonnes attendues).
2. Associez les colonnes du fichier aux champs du CRM.
3. Lancez la simulation : elle montre les créations, mises à jour et refus.
4. Corrigez les refus si besoin, puis validez l'import.`),
  'portefeuilles': p('Portefeuilles agents', `Chaque client a un seul agent à la fois. Une réaffectation garde l'historique.

- Cochez des clients puis « Affecter à un agent ».
- « Organiser un intérim » confie un portefeuille pendant une absence ; tout revient à la date de fin.
- « Transférer le portefeuille » confie définitivement les clients d'un agent à un autre.`),
  'campagnes': p('Campagnes de relance', `Un envoi groupé de relances : créer, soumettre à validation, puis exécuter à la date prévue.

1. « Nouvelle campagne » : critères des factures, modèle de message, date de départ.
2. Soumettez : la personne habilitée valide ou renvoie pour modification.
3. Validée, la campagne part à sa date ; suivez message par message ce qui est parti.`),
  'campagnes/nouvelle': p('Nouvelle campagne', `Les critères se combinent pour choisir les factures à relancer.

1. Choisissez le type de relance et les critères (agence, segment, retard…).
2. Choisissez le modèle de message et la date de départ.
3. « Calculer le récapitulatif » montre les clients et montants visés ; retirez-en au besoin.
4. Enregistrez le brouillon ou soumettez à validation.`),
  'campagnes/:id/modifier': p('Modifier la campagne', `Corrigez les critères, le modèle ou la date, recalculez le récapitulatif, puis soumettez de nouveau à validation.`),
  'campagnes/:id': p('Suivi de campagne', `L'état de la campagne et de chacun de ses messages : à envoyer, envoyé, lu, en échec.

- Selon vos droits : modifier, soumettre, valider, renvoyer pour modification, lancer maintenant ou annuler.
- « Envoyer maintenant » sur une ligne fait partir ce message tout de suite.`),
  'delais-relance': p('Délais de relance', `Quand une facture est relancée par rapport à son échéance, selon l'agence et le segment du client.

1. « Nouvelle règle » : choisissez l'agence et/ou le segment.
2. Indiquez la fenêtre avant échéance, le premier jour après échéance et l'écart entre deux relances.
3. Une case vide reprend le niveau au-dessus : agence et segment, puis agence, puis segment, puis les délais par défaut.`),
  'modeles': p('Modèles de messages', `Les messages types des relances, avec leurs variables.

- Les variables s'écrivent entre accolades, par exemple {nom_pharmacie}, et sont remplacées pour chaque facture à l'envoi.
- Un modèle peut être exporté en Word pour relecture.
- Un modèle n'est jamais supprimé : il est archivé.`),
  'discussions': p('Discussions', `Les échanges WhatsApp avec chaque client : messages partis et réponses reçues. Vous répondez depuis votre numéro.

- Choisissez une discussion à gauche, écrivez en bas, joignez un fichier si besoin.
- « Nouvelle discussion » écrit à un client qui ne vous a pas encore écrit.`),
  'reponses': p('Réponses des clients', `Copie des messages reçus sur les WhatsApp professionnels des agents. On répond depuis son téléphone, comme d'habitude.`),
  'envoi-masse': p('Envoi WhatsApp en masse', `Un même message envoyé depuis votre numéro à des clients choisis, une liste de diffusion, un fichier ou des numéros collés.

1. Écrivez le message ou choisissez un modèle ; ajoutez les pièces jointes.
2. Choisissez les destinataires.
3. « Vérifier les destinataires » écarte doublons, numéros invalides et désabonnés.
4. Envoyez tout de suite ou programmez le départ. Suivez ensuite l'envoi ligne par ligne.`),
  'historique-envois': p('Historique des envois', `Tous les messages partis : relances, promotions, réponses des discussions et envois en masse, par WhatsApp, e-mail ou SMS.

- Filtrez puis exportez en CSV.
- Un message encore en attente peut partir tout de suite avec « Envoyer maintenant ».`),
  'numeros': p('Numéros WhatsApp', `Chaque agent envoie depuis son numéro professionnel, relié par WhatsApp Web (QR code) ou par l'API officielle de Meta.

1. « Scanner le QR code » puis, sur le téléphone : WhatsApp > Appareils connectés > Connecter un appareil.
2. Le téléphone doit rester allumé et connecté à internet.
3. En cas de souci, « Mettre en pause » puis « Vérifier et reprendre ».

La colonne Rythme dit pourquoi un numéro attend : longue pause après une série de messages, plafond par heure ou par jour atteint, ou repos après des échecs répétés. Les envois reprennent seuls à l'heure indiquée. Pour un numéro neuf, activez la montée en charge.`),
  'marketing': p('Ma journée Marketing', `Votre page d'arrivée dans le Marketing.

- Les campagnes à valider ou à corriger, celles en cours et programmées aujourd'hui.
- Les réponses des clients reçues aujourd'hui.
- Les promotions qui commencent ou finissent et les événements à venir, sur le nombre de jours réglé dans Paramètres > Marketing.`),
  'marketing/tableau-de-bord': p('Tableau de bord Marketing', `Messages envoyés, lus, réponses et désabonnements sur la période, par campagne et par canal.`),
  'marketing/campagnes': p('Campagnes', `Un type (promotion, information ou événement), une liste de diffusion, un modèle, une date. Le message part du numéro de la personne qui prépare la campagne, après validation.

1. « Nouvelle campagne » : liste, modèle, promotion éventuelle, affiche et date.
2. Soumettez à validation.
3. Suivez les envois, lectures et réponses dans la campagne.`),
  'marketing/campagnes/:id': p('Suivi de campagne promotionnelle', `L'état de la campagne et de chacun de ses messages : à envoyer, envoyé, lu, en échec.

- Selon vos droits : modifier, soumettre, valider, lancer maintenant ou annuler.`),
  'marketing/listes': p('Listes de diffusion', `Les clients visés par les campagnes marketing : par critères, complétés ou corrigés client par client.

- Les clients qui ont répondu STOP sont toujours écartés.
- Une liste n'est jamais supprimée : elle est archivée.`),
  'marketing/listes/:id': p('Liste de diffusion', `Les clients de la liste. Excluez, ajoutez ou réintégrez des clients un par un ; désabonnez ou réabonnez à la demande du client.`),
  'relances/listes': p('Listes de diffusion des relances', `Les listes du Recouvrement, choisies dans l'envoi en masse : par critères, complétées ou corrigées client par client.

- Elles ne se voient jamais depuis le Marketing, et celles du Marketing ne se voient pas ici.
- Le STOP marketing n'en retire personne.
- Une liste n'est jamais supprimée : elle est archivée.`),
  'relances/listes/:id': p('Liste de diffusion des relances', `Les clients de la liste. Excluez, ajoutez ou réintégrez des clients un par un.`),
  'marketing/catalogue': p('Catalogue des produits', `Les produits mis en avant dans les promotions.

- Importez le fichier Excel ou saisissez les produits un par un.
- Marquez un produit en rupture ou de retour en stock, puis annoncez son retour aux clients.
- Un produit n'est jamais supprimé : il est archivé.`),
  'marketing/catalogue/import': p('Importer des produits', `Choisissez le fichier, associez les colonnes, lancez la simulation, puis validez l'import.`),
  'marketing/promotions': p('Promotions', `Une période et des produits du catalogue avec leur avantage : unités gratuites ou prix promotionnel.

1. « Nouvelle promotion » : nom, code et dates.
2. Ajoutez les produits et leur avantage.
3. Activez-la ou programmez-la ; elle se termine d'elle-même après sa date de fin.`),
  'marketing/promotions/import': p('Importer des produits en promotion', `Choisissez le fichier, associez les colonnes, lancez la simulation, puis validez l'import.`),
  'marketing/promotions/:id': p('Promotion', `Les produits de la promotion et leur avantage. Ajoutez ou retirez des produits, exportez la liste, activez ou programmez la promotion.`),
  'marketing/modeles': p('Modèles de messages', `Les messages types des campagnes marketing, avec leurs variables et leur affiche.

- Les variables s'écrivent entre accolades, par exemple {nom_pharmacie}.
- Un modèle n'est jamais supprimé : il est archivé.`),
  'marketing/informations': p('Informations clients', `Problèmes de livraison, gardes, jours fériés, horaires, fermetures, alertes et retraits de produit.

1. « Nouvelle information » : type, titre et message.
2. « Annoncer » prépare la campagne programmée à la date et à l'heure voulues.`),
  'marketing/evenements': p('Calendrier des événements', `Fêtes, jours fériés et fermetures d'agence annoncés aux clients par des messages de courtoisie.

- Un événement annuel revient chaque année à la même date.
- Les fêtes à date variable (Pâques, Ramadan, Tabaski…) se saisissent pour l'année.`),
  'marketing/numero': p('Numéros WhatsApp', `Le numéro depuis lequel partent vos campagnes marketing. Reliez-le en scannant le QR code avec votre téléphone, qui doit rester allumé et connecté.`),
  'agences': p('Agences', `Les agences du réseau. Le code d'import est celui des fichiers de clients et de factures. Une agence archivée sort des listes de choix et garde son historique.`),
  'parametres': p('Paramètres', `Les réglages de l'application, rangés par thème à gauche. Chaque changement prend effet tout de suite et reste tracé dans le journal.

- Aide et guidage : interrupteur pour toute l'entreprise et textes des infobulles et des panneaux d'aide.
- Référentiels : régions, villes, communes, tournées et segments.`),
  'fournisseurs-sms': p('Fournisseurs SMS', `Les relances SMS partent par le fournisseur par défaut. Chaque fournisseur est décrit par son API ; un essai vérifie le réglage.`),
  'utilisateurs': p('Utilisateurs et habilitations', `Chaque compte reçoit un rôle par module (Recouvrement, Marketing) et une ou plusieurs agences.

1. « Nouveau compte » : identité, agences, rôle.
2. Transmettez le mot de passe provisoire ; l'utilisateur le remplace à sa première connexion.
3. Un compte n'est jamais supprimé : il est désactivé et son historique reste.`),
  'roles': p('Rôles et droits', `Cochez, pour chaque rôle, les écrans et les actions qu'il ouvre. Le rôle s'attribue ensuite dans Utilisateurs et habilitations. Un rôle n'est jamais supprimé : il est désactivé.`),
  'journal': p('Journal des actions', `Qui a créé, modifié, désactivé ou réactivé quoi, et quand. Filtrez par période, utilisateur, type de donnée ou client, puis imprimez ou exportez.`),
  'support': p('Centre de support', `Posez une question ou signalez un problème, suivez la réponse dans le ticket et consultez la FAQ.

- Décrivez ce que vous faisiez, ce que vous attendiez et ce qui s'est passé.
- Si un message d'erreur affiche une référence, recopiez-la.`),
  'mot-de-passe': p('Mon mot de passe', `Choisissez un mot de passe que vous êtes seul à connaître. Il doit respecter les règles de sécurité affichées.`)
};

// Les listes des relances reprennent les textes de celles du Marketing (meme ecran, autre module)
for (const table of [AIDE_BOUTONS, AIDE_PAGES]) {
  for (const cle of Object.keys(table).filter(c => c.startsWith('marketing/listes'))) {
    table['relances/' + cle.slice('marketing/'.length)] ??= table[cle];
  }
}
