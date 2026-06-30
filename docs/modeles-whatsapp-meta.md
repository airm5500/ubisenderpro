# Modèles WhatsApp Meta (canal API officiel) — Référence UbiSenderPro

Ce document liste les modèles (templates) à créer dans **WhatsApp Manager**
(business.facebook.com → WhatsApp Manager → Modèles de messages) pour le **canal
API officiel**, avec pour chacun :

- le **corps** prêt à coller (variables positionnelles `{{n}}`) ;
- les **exemples** demandés par Meta à la soumission ;
- le **mapping `paramsCorps`** à renseigner dans UbiSenderPro
  (Paramètres → Modèles de messages), qui relie chaque `{{n}}` à une variable.

À l'envoi, chaque `{{n}}` est rempli :

- soit par une **variable par contact** (résolue par destinataire) :
  `nom_contact`, `nom_compte`, `civilite_complete`, `segmentation`, `societe`… ;
- soit par une **variable de contexte de la campagne** (figée à la validation) :
  `mois_promotion`, `date_fin`, `avantage_ug`, `nombre_produits`, `jours_restants`…

---

## ⚠️ Règles Meta sur les paramètres `{{n}}`

Un paramètre de corps Meta **ne peut pas** contenir :

- de **saut de ligne**, de **tabulation**, ni **plus de 4 espaces** consécutifs ;
- une **valeur vide** à l'envoi (sinon l'envoi échoue).

Règles de rédaction du corps :

- pas de variable **au tout début** ni **à la toute fin** du corps ;
- pas de **deux variables collées** (`{{1}} {{2}}` sans texte entre elles) ;
- toujours fournir un **exemple** par variable.

Conséquences pour UbiSenderPro :

- ❌ `liste_produits` (multi-lignes) **interdit** en paramètre → la liste passe
  dans le **fichier joint** (bulletin .xlsx attaché en en-tête `Document`).
- ❌ variables **facultatives** (ex. `nouvelle_estimation_livraison`) à éviter
  (vides = échec d'envoi).
- ❌ `message` libre (infos générales) → garder ces envois sur le **canal WEB**.

---

## Procédure (pour chaque modèle)

1. **Créer** le modèle dans WhatsApp Manager (catégorie `Marketing`, langue
   `Français (fr)`), coller le corps + les exemples, soumettre → attendre
   l'approbation Meta.
2. Dans **UbiSenderPro → Paramètres → Modèles**, ouvrir le modèle auto
   correspondant (ou « 📥 Importer depuis Meta »).
3. Renseigner **Nom du modèle Meta**, **Langue (`fr`)**, **paramsCorps** (ci-dessous),
   puis **Approbation = APPROUVE**.
4. Créer une **campagne** avec **canal = API** et ce modèle.

> Les modèles Dispo/Promo mensuelle/Promo avec pièce jointe utilisent un
> **en-tête `Document`** : le fichier .xlsx est attaché automatiquement.

---

## 1) Promotions (4 modèles)

### `annonce_mensuelle_ug` — Annonce mensuelle
*En-tête : Document*
```
Bonjour {{1}} 📢
Découvrez les promotions UG du mois de {{2}} chez {{3}}.
Offres valables du {{4}} au {{5}}, dans la limite des stocks.
📎 Consultez le fichier Excel joint et commandez sur EXTRANET.
Direction Commerciale
```
- Exemples : `Dupont` · `Juillet` · `Ubipharm` · `01/07/2026` · `31/07/2026`
- **paramsCorps :** `nom_contact,mois_promotion,nom_compte,date_debut_globale,date_fin_globale`

### `lancement_promo_ug` — Lancement
*En-tête : Document*
```
Bonjour {{1}} 🚀
La promotion « {{2}} » démarre aujourd'hui !
Bénéficiez de {{3}} sur {{4}} produit(s) sélectionné(s).
📅 Offre valable du {{5}} au {{6}}.
📎 Consultez le fichier Excel joint et commandez sur EXTRANET.
Direction Commerciale
```
- Exemples : `Dupont` · `Promo Été` · `jusqu'à 10 % d'unités gratuites` · `12` · `01/07/2026` · `31/07/2026`
- **paramsCorps :** `nom_contact,nom_promotion,avantage_ug,nombre_produits,date_debut,date_fin`

### `rappel_derniers_jours_ug` — Derniers jours (J-3)
*En-tête : Document*
```
Bonjour {{1}} ⏳
L'offre « {{2}} » arrive bientôt à expiration.
Il reste {{3}} jour(s) pour profiter de {{4}} sur {{5}} produit(s).
📅 Date de fin : {{6}}.
📎 Préparez votre commande via EXTRANET dès maintenant.
Direction Commerciale
```
- Exemples : `Dupont` · `Promo Été` · `3` · `jusqu'à 10 % d'unités gratuites` · `12` · `31/07/2026`
- **paramsCorps :** `nom_contact,nom_promotion,jours_restants,avantage_ug,nombre_produits,date_fin`

### `derniere_chance_ug` — Dernière chance (J-1)
*En-tête : Document*
```
Bonjour {{1}} ⏰
Dernière chance pour la promotion « {{2}} » !
Il ne reste que {{3}} jour(s) pour bénéficier de {{4}}.
📅 Fin de l'offre : {{5}}.
📎 Finalisez votre commande sur EXTRANET avant cette date.
Direction Commerciale
```
- Exemples : `Dupont` · `Promo Été` · `1` · `jusqu'à 10 % d'unités gratuites` · `31/07/2026`
- **paramsCorps :** `nom_contact,nom_promotion,jours_restants,avantage_ug,date_fin`

---

## 2) Disponibilités / Ruptures (5 modèles)

*En-tête : Document (bulletin .xlsx des produits attaché automatiquement).*

### `dispo_disponibilite` — Annonce de disponibilité
```
Bonjour {{1}} 📢
Bonne nouvelle : {{2}} produit(s) sont à nouveau disponibles chez {{3}} (agence de {{4}}).
📎 Le détail des produits figure dans le fichier joint.
💻 Passez commande sur EXTRANET pour sécuriser votre stock.
Direction Commerciale
```
- Exemples : `Dupont` · `5` · `Ubipharm` · `Abidjan`
- **paramsCorps :** `nom_contact,nombre_produits,societe,agence`

### `dispo_retour_rupture` — Retour de rupture
```
Bonjour {{1}} 🌟
Bonne nouvelle : {{2}} produit(s) en rupture sont de retour en stock chez {{3}}.
⚠️ Quantités limitées : réservez avant le {{4}}.
📎 Le détail des produits figure dans le fichier joint.
Direction Commerciale
```
- Exemples : `Dupont` · `5` · `Ubipharm` · `15/07/2026`
- **paramsCorps :** `nom_contact,nombre_produits,societe,date_limite_reservation`

### `dispo_risque_rupture` — Alerte risque de rupture
```
Bonjour {{1}} 🚨
Certains produits présentent un risque de rupture au {{2}} chez {{3}}.
📄 Le bulletin joint liste les produits concernés.
Nous vous recommandons d'anticiper vos commandes.
Direction Commerciale
```
- Exemples : `Dupont` · `30/06/2026` · `Ubipharm`
- **paramsCorps :** `nom_contact,date_bulletin,societe`

### `dispo_stock_limite` — Stock limité
```
Bonjour {{1}} ⏳
{{2}} produit(s) sont disponibles en quantités limitées chez {{3}} (agence de {{4}}).
📎 Détail dans le fichier joint — commandez vite sur EXTRANET.
Direction Commerciale
```
- Exemples : `Dupont` · `4` · `Ubipharm` · `Abidjan`
- **paramsCorps :** `nom_contact,nombre_produits,societe,agence`

### `dispo_rupture_confirmee` — Rupture confirmée
```
Bonjour {{1}} 🚨
Nous vous informons que {{2}} produit(s) sont actuellement en rupture chez {{3}}.
📅 Retour prévisionnel : {{4}}.
📎 Le détail figure dans le fichier joint.
Merci de votre compréhension.
Direction Commerciale
```
- Exemples : `Dupont` · `3` · `Ubipharm` · `20/07/2026`
- **paramsCorps :** `nom_contact,nombre_produits,societe,date_retour`

---

## 3) Informations / Alertes opérationnelles (6 modèles API)

### `info_information_garde` — Information / rappel de garde
```
Bonjour {{1}} 📢
À l'occasion de {{2}}, transmettez vos commandes à l'agence de {{3}} avant {{4}}.
📦 Modalités : {{5}}
💻 Privilégiez EXTRANET pour la saisie.
Pharmacien de garde : {{6}} ({{7}}).
Direction Commerciale
```
- Exemples : `Dupont` · `Fête de l'Indépendance` · `Abidjan` · `12h00` · `Livraison le matin` · `Dr Kouassi` · `+225 07 00 00 00`
- **paramsCorps :** `nom_contact,jour_ferie,agence,heure_limite_commande,consignes_livraison,pharmacien_garde,telephone_pharmacien`

### `info_jour_ferie` — Jour férié
```
Bonjour {{1}} 📢
À l'occasion de {{2}}, l'organisation des commandes est adaptée.
📦 Modalités : {{3}}
💻 Transmettez vos commandes via EXTRANET avant {{4}}.
Merci de votre confiance.
Direction Commerciale
```
- Exemples : `Dupont` · `Fête du Travail` · `Livraison le matin` · `12h00`
- **paramsCorps :** `nom_contact,jour_ferie,consignes_livraison,heure_limite_commande`

### `info_retard_livraison` — Retard de livraison
```
Bonjour {{1}} 📢
Nous rencontrons un retard de livraison sur {{2}}.
Nos équipes sont pleinement mobilisées pour vous livrer au plus vite.
Merci de votre patience et de votre compréhension.
Direction Commerciale
```
- Exemples : `Dupont` · `Tournée Nord`
- **paramsCorps :** `nom_contact,agence_ou_tournee`

### `info_reprise_livraison` — Reprise des livraisons
```
Bonjour {{1}} 📢
Bonne nouvelle : les livraisons sur {{2}} reprennent normalement.
💻 Vous pouvez transmettre vos commandes via EXTRANET.
Merci de votre confiance.
Direction Commerciale
```
- Exemples : `Dupont` · `Tournée Nord`
- **paramsCorps :** `nom_contact,agence_ou_tournee`

### `info_annulation_tournee` — Annulation de tournée
```
Bonjour {{1}} 📢
Nous vous informons de l'annulation de la tournée {{2}}.
Nos équipes reviennent vers vous pour la reprogrammation.
Nous vous présentons nos excuses pour la gêne occasionnée.
Direction Commerciale
```
- Exemples : `Dupont` · `Tournée Est`
- **paramsCorps :** `nom_contact,tournee`

### `anniversaire_client` — Anniversaire
```
Joyeux anniversaire {{1}} 🎉
Toute l'équipe de {{2}} vous souhaite une excellente journée 🎂
Merci pour votre confiance et ce précieux partenariat 🙏
Au plaisir de vous accompagner longtemps !
```
- Exemples : `M. Dupont` · `Ubipharm`
- **paramsCorps :** `civilite_complete,societe` *(les deux résolus par contact)*

### À garder sur le canal WEB (texte libre)

`INFORMATION_GENERALE`, `ALERTE_URGENTE`, `MODIFICATION_HORAIRES`,
`FERMETURE_AGENCE` reposent sur un `message` libre (souvent multi-lignes),
incompatible avec un template Meta figé → utiliser le **canal WEB**.

---

## Récapitulatif des `paramsCorps`

| Source | Nom Meta | paramsCorps |
|---|---|---|
| Promo — annonce mensuelle | `annonce_mensuelle_ug` | `nom_contact,mois_promotion,nom_compte,date_debut_globale,date_fin_globale` |
| Promo — lancement | `lancement_promo_ug` | `nom_contact,nom_promotion,avantage_ug,nombre_produits,date_debut,date_fin` |
| Promo — derniers jours (J-3) | `rappel_derniers_jours_ug` | `nom_contact,nom_promotion,jours_restants,avantage_ug,nombre_produits,date_fin` |
| Promo — dernière chance (J-1) | `derniere_chance_ug` | `nom_contact,nom_promotion,jours_restants,avantage_ug,date_fin` |
| Dispo — disponibilité | `dispo_disponibilite` | `nom_contact,nombre_produits,societe,agence` |
| Dispo — retour rupture | `dispo_retour_rupture` | `nom_contact,nombre_produits,societe,date_limite_reservation` |
| Dispo — risque rupture | `dispo_risque_rupture` | `nom_contact,date_bulletin,societe` |
| Dispo — stock limité | `dispo_stock_limite` | `nom_contact,nombre_produits,societe,agence` |
| Dispo — rupture confirmée | `dispo_rupture_confirmee` | `nom_contact,nombre_produits,societe,date_retour` |
| Info — garde | `info_information_garde` | `nom_contact,jour_ferie,agence,heure_limite_commande,consignes_livraison,pharmacien_garde,telephone_pharmacien` |
| Info — jour férié | `info_jour_ferie` | `nom_contact,jour_ferie,consignes_livraison,heure_limite_commande` |
| Info — retard | `info_retard_livraison` | `nom_contact,agence_ou_tournee` |
| Info — reprise | `info_reprise_livraison` | `nom_contact,agence_ou_tournee` |
| Info — annulation tournée | `info_annulation_tournee` | `nom_contact,tournee` |
| Info — anniversaire | `anniversaire_client` | `civilite_complete,societe` |

---

## Annexe — Variables disponibles

### Par contact (résolues par destinataire à l'envoi)
`nom_contact`, `nom_compte`, `civilite`, `civilite_complete`, `segmentation`,
`societe`, `ville`, `region`, `email`, `telephone`.

### Contexte campagne (figées à la validation de la proposition)

- **Promo (lancement / rappel)** : `nom_promotion`, `date_debut`, `date_fin`,
  `avantage_ug`, `nombre_produits`, `taux_ug_max`, `jours_restants` (rappels).
- **Promo annonce mensuelle** : `mois_promotion`, `date_debut_globale`,
  `date_fin_globale`, `nombre_promotions`, `nombre_produits`, `avantage_ug`,
  `dates_fin_promotions`.
- **Disponibilité / rupture** : `societe`, `agence`, `nombre_produits`,
  `date_bulletin`, `date_retour`, `date_limite_reservation`,
  `lien_reservation`, `liste_produits` *(réservé au fichier joint)*.
- **Information** : `agence`, `tournee`, `agence_ou_tournee`, `jour_ferie`,
  `heure_limite_commande`, `consignes_livraison`, `pharmacien_garde`,
  `telephone_pharmacien`, `direction_signataire`,
  `nouvelle_estimation_livraison` *(facultative — à éviter en paramètre API)*.

> `avantage_ug` produit toujours une phrase complète
> (« jusqu'à 10 % d'unités gratuites » ou « des unités gratuites »),
> donc le texte reste correct quel que soit le taux.
