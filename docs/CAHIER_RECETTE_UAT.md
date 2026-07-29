# Cahier de recette UAT — UbiSmartCRM Pro v2.0

> Tests d'acceptation à dérouler sur l'environnement de test après chaque
> livraison. Pour chaque scénario : suivre les étapes, comparer au **résultat
> attendu**, cocher **OK/KO** et noter l'anomalie le cas échéant.
>
> Pré-requis : base migrée (V56), un compte ADMIN, SMTP configuré (pour les
> scénarios e-mail), une session WhatsApp Web connectée (pour les envois).

Légende priorité : 🔴 bloquant · 🟠 majeur · 🟢 confort

---

## 1. Connexion & session

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 1.1 | 🔴 | Ouvrir l'application | Page de connexion « UbiSmartCRM Pro · v2.0 », logo animé | | |
| 1.2 | 🔴 | Login/mot de passe valides | Accès à l'application, header « Bienvenu(e), <nom> » en gras | | |
| 1.3 | 🔴 | Identifiants invalides | Message « Identifiants invalides », pas d'accès | | |
| 1.4 | 🔴 | **F5** (rafraîchir la page) | La session est conservée (pas de retour au login) | | |
| 1.5 | 🟠 | Dupliquer l'onglet navigateur | Les 2 onglets fonctionnent avec la même session | | |
| 1.6 | 🟠 | Rester inactif > `delai_deconnexion` minutes | Déconnexion automatique, retour au login | | |
| 1.7 | 🔴 | Bouton rond rouge (déconnexion) | Session clôturée, retour au login ; F5 ne reconnecte pas | | |

## 2. Header & navigation

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 2.1 | 🟢 | Observer le header | Bleu nuit ; cloche + pastille entières et centrées ; avatar de la taille de la cloche ; À propos APRÈS la déconnexion | | |
| 2.2 | 🟠 | Clic cloche / re-clic | Volet notifications s'ouvre **sous la cloche** ; re-clic le ferme ; pas d'empilement ; sections repliées, types vides absents | | |
| 2.3 | 🟢 | À propos | « UbiSmartCRM Pro », version **2.0.0**, développeur Hermann NZI | | |
| 2.4 | 🟠 | Menu latéral | Dégradé teal ; libellés complets (« Suivi Relance et Recouvrements » non coupé) ; menu actif = carte blanche | | |
| 2.5 | 🟢 | Onglets des modules | Style « dossier » : actif blanc, inactifs gris-bleu soutenu, pastille ✳ animée sur l'actif | | |

## 3. Tableau de bord

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 3.1 | 🟠 | Affichage général | Colonnes « métro » par section (étiquettes colorées), tuiles dégradées avec icône en filigrane ; **courbe fixée en bas**, pas de scroll global | | |
| 3.2 | 🟠 | Survol d'un point de la courbe | Info-bulle : série + jour + volume | | |
| 3.3 | 🟠 | ⚙ Personnaliser : décocher des indicateurs, réordonner (▲/▼ sections ET indicateurs), Enregistrer (pour moi) | Le rendu reflète les choix ; ils survivent au F5 | | |
| 3.4 | 🟠 | (ADMIN) « 💾 Défaut (tous) » puis se connecter avec un AUTRE utilisateur jamais personnalisé | L'autre utilisateur voit la configuration par défaut | | |
| 3.5 | 🟢 | « Revenir au défaut » | La personnalisation locale s'efface, retour au défaut partagé | | |
| 3.6 | 🟠 | Clic sur une tuile (ex. Comptes clients) | Navigation vers le menu correspondant | | |

## 4. Comptes clients

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 4.1 | 🔴 | ➕ Nouveau client : fenêtre | 2 colonnes, **aucun scroll** ; champs * : Code, Nom, Entreprise, Segmentation, Agence | | |
| 4.2 | 🔴 | Enregistrer sans numéro | Refus : « Ajoutez au moins un numéro de téléphone » | | |
| 4.3 | 🔴 | Ajouter un numéro | Préfixe pays pré-rempli, curseur après ; WhatsApp coché ; 1er = Principal ; 🗑️ **sur la ligne** | | |
| 4.4 | 🔴 | Créer un client complet | Toast de confirmation ; la liste se rafraîchit ; segmentation en **pastille colorée** (Gold = or…) | | |
| 4.5 | 🟠 | Bouton Contacts d'un client | Écran **numéros uniquement** (pas de civilité/fonction/anniversaire) | | |
| 4.6 | 🟠 | Modifier le client : date de naissance | Sur la fenêtre principale ; sauvegarde → anniversaire du contact principal | | |
| 4.7 | 🟠 | Import clients (assistant) | Mapping colonnes ; une colonne à numéros multiples (a/b) crée un contact par numéro, 1er = principal WhatsApp ; **Exporter un exemplaire** disponible | | |
| 4.8 | 🟠 | Liste de diffusion → Choisir des clients | Les membres déjà ajoutés n'apparaissent plus dans le sélecteur | | |
| 4.9 | 🟠 | Segmentation (onglet) | Modifier **sur la ligne** ; libellés en pastilles colorées | | |

## 5. Campagnes / Marketing / Promotions

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 5.1 | 🔴 | Nouvelle campagne (wizard) | Bouton **Annuler** présent ; si brouillon déjà créé, proposition de le supprimer | | |
| 5.2 | 🔴 | Construire + Lancer une campagne (WA Web) | Destinataires calculés ; **barre de progression en direct** (% + « k / n ») jusqu'à 100 % ; « Continuer en arrière-plan » possible ; grille rafraîchie | | |
| 5.2b | 🟠 | Envoi en masse WhatsApp Web (immédiat) | Même **barre de progression** (% + envoyés/total, échecs en rouge) ; à la fermeture, proposition de réinitialiser la vue | | |
| 5.2c | 🟠 | **Relancer les échecs** (campagne 🔄 et envoi en masse 🔄) | Barre de progression en direct pendant la reprise ; grille rafraîchie à la fin | | |
| 5.2d | 🟢 | **Envoi unitaire** (message depuis Discussions, relance individuelle 📨) | Bandeau discret en bas à droite « envoi en cours… 0 / 1 » puis « 100 % — 1 / 1 ✅ » (ou ❌) ; **non bloquant** (la saisie reste possible) | | |
| 5.3 | 🟠 | Grille Campagnes | Bandeau KPI en cartes encadrées au-dessus | | |
| 5.4 | 🟠 | Promotions : créer sans dates | Refus clair « champs obligatoires (*) » — pas d'« erreur technique » | | |
| 5.5 | 🟠 | Promotions : code auto | Code 4 chiffres pré-rempli, modifiable ; Responsable en bleu | | |
| 5.6 | 🟠 | Produit de promotion depuis le catalogue | CIP7/CIP13/nom **grisés** après sélection | | |
| 5.7 | 🟠 | Propositions : Valider / Rejeter | Indicateur d'attente ; tous les sous-onglets se rafraîchissent seuls | | |
| 5.8 | 🟠 | Proposition non liée : 🗑️ | Suppression (privilège marketing/SUPPRIMER requis) | | |

## 6. Recouvrement

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 6.1 | 🟠 | Tableau de bord | 7 bulles KPI sur **une ligne** | | |
| 6.2 | 🔴 | Nouvelle fiche : choisir un client à segmentation | Segment auto-rempli et **verrouillé** ; entreprise affichée | | |
| 6.3 | 🟠 | Rouvrir une fiche avec profil/statut renseignés | Ces 2 champs **verrouillés** (grisés) | | |
| 6.4 | 🟠 | Enregistrer une fiche incomplète | Message clair + champ fautif en rouge (pas d'« erreur technique ») | | |
| 6.5 | 🟠 | Modèles : « Modèles standard » | Confirmation → génération ; bouton **désactivé** quand tout existe ; réactivé après suppression d'un modèle ; actions ✏️/🗑️ par ligne | | |
| 6.6 | 🟠 | Imports (fiches/créances/règlements) | Chaque section ouvre l'**assistant à mapping** (exemplaire téléchargeable, simulation, rapport) | | |
| 6.7 | 🟠 | Campagnes : Aperçu sans fiche correspondante | « 0 client ciblé » avec explication ; bouton **Réinitialiser l'écran** vide les champs | | |
| 6.8 | 🔴 | Relance individuelle (📨) | Envoi WhatsApp/e-mail ; visible dans Historique du module | | |

## 7. Centre de support

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 7.1 | 🔴 | Me contacter (avec `support.email` + SMTP) | Demande archivée ; e-mail reçu par l'éditeur ; **accusé** reçu par l'utilisateur | | |
| 7.2 | 🔴 | Nouveau ticket (utilisateur simple) | N° TCK-AAAA-NNNN ; visible dans « Mes tickets » ; conversation OK | | |
| 7.3 | 🟠 | (ADMIN/SUPPORT) Traiter le ticket | Changer statut + M'affecter → tracés dans la conversation ; l'utilisateur voit la réponse | | |
| 7.4 | 🔴 | **Auto-ticket** : provoquer une erreur JS (console : `Usp.nimporteQuoi()`) ou une 500 | Événement au journal 🐞 **ET** ticket `[AUTO]` créé **ET** e-mail reçu à `support.email` | | |
| 7.5 | 🟠 | Répéter la même erreur | PAS de nouveau ticket ; compteur d'occurrences +1 | | |
| 7.6 | 🟠 | Santé | Cartes vertes (base/WA/e-mail/serveur) cohérentes avec la réalité | | |
| 7.7 | 🟠 | Grille Utilisateurs | Le compte `support` n'y apparaît PAS ; il peut pourtant se connecter | | |

## 8. Licence

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 8.1 | 🔴 | Licence → État (sans licence, `licence.obligatoire=false`) | Statut AUCUNE « mode libre » ; aucune restriction dans l'app | | |
| 8.2 | 🔴 | Générer une demande (.licreq) → UbiLicense Manager → générer .lic → Importer | Statut **Active**, société/dates/modules affichés ; journal tracé | | |
| 8.3 | 🔴 | Altérer 1 caractère de la clé et importer | Refus « signature invalide » | | |
| 8.4 | 🟠 | Passer `licence.obligatoire=true` avec licence à modules réduits | Les menus hors licence disparaissent pour tous ; leurs API renvoient 403 | | |
| 8.5 | 🟠 | Licence expirée (émettre une licence à date passée) + grâce dépassée | Bandeau rouge ; lancement de campagne/import → message « licence expirée » ; consultation OK ; écran Licence accessible | | |
| 8.6 | 🟠 | Importer une licence valide par-dessus | Restrictions levées immédiatement, sans réinstallation | | |
| 8.7 | 🟢 | Remettre `licence.obligatoire=false` | Retour au mode libre | | |

## 9. Robustesse & divers

| # | P | Scénario | Résultat attendu | OK/KO | Anomalie |
|---|---|---|---|---|---|
| 9.1 | 🟠 | Saisir des lettres dans un champ nombre (contournement : montant, etc.) | Message clair « pas au bon format » + champ surligné — jamais « erreur technique » | | |
| 9.2 | 🟠 | Import CSV piégé (guillemet non fermé) | Rejet propre avec message ; pas de plantage | | |
| 9.3 | 🟠 | Historique des envois : filtre Statut + Rafraîchir | Filtre appliqué ; icône ↻ tourne pendant le chargement | | |
| 9.4 | 🟢 | Vérification de numéros | Chargement fichier + « Exporter un exemplaire » | | |
| 9.5 | 🟠 | Paramètres → Référentiels : Assistant d'import | Mapping colonnes + rapport lues/créées/ignorées | | |
| 9.6 | 🔴 | **Session WhatsApp Web « zombie »** : après une longue coupure, une réponse client arrive illisible (déchiffrement échoué) | Le statut reste « Connecté » MAIS la colonne Réception passe **⚠ À reconnecter** ; une **bannière rouge** s'affiche partout avec « Reconnecter maintenant » → déconnexion + QR ; après rescan, la réception revient et la bannière disparaît | | |
| 9.7 | 🟢 | Session WhatsApp Web saine | Colonne Réception « ✔ OK · reçu il y a X » ; carte Santé WhatsApp Web verte (aucune session dégradée) | | |

---

## Synthèse de la campagne

| Date | Testeur | Version | Total OK | Total KO | Verdict (GO / NO-GO) |
|---|---|---|---|---|---|
| | | | | | |

**Règle de décision suggérée** : GO si 100 % des 🔴 sont OK et ≥ 90 % des 🟠.
