# Note de migration — livraison des lots A → F + pièces jointes

Cette note couvre le passage en production de l'ensemble des évolutions livrées
sur la branche `claude/tender-wozniak-nnzz3j`.

## 1. Principe : migrations automatiques (Flyway)

Le schéma de base est versionné avec Flyway. **Au démarrage de l'application**,
le `Bootstrap` exécute `flyway.repair()` puis `flyway.migrate()` : les nouvelles
migrations s'appliquent **automatiquement**, dans l'ordre, une seule fois.

➡️ Aucune commande SQL manuelle à lancer. Il suffit de **déployer le nouveau WAR
et de redémarrer** l'application.

## 2. Migrations ajoutées dans cette livraison

| Version | Fichier | Effet |
|---|---|---|
| **V49** | `V49__rec_envoi_piece_jointe.sql` | `usp_rec_envoi` : ajout `piece_jointe VARCHAR(255)` (traçabilité des pièces jointes de relance) |
| **V50** | `V50__client_entreprise.sql` | `usp_client` : ajout `entreprise VARCHAR(255)` (nom de l'officine) |
| **V51** | `V51__audience_segments_multiples.sql` | `usp_info_evenement` **et** `usp_dispo_evenement` : ajout `segmentation_ids VARCHAR(255)` (ciblage multi-segments) |

Toutes ces migrations sont **additives** (ajout de colonnes nullables) : aucune
donnée existante n'est modifiée ou supprimée, aucune colonne n'est renommée en base.

> Les renommages « N° client → Code client », « Nom du compte → Nom client » sont
> **uniquement des libellés d'écran** : la base garde `numero_client` / `nom_compte`.

## 3. Éléments créés automatiquement au démarrage (hors Flyway)

- **Modèle de message « Retrait de produit »** (`INFO_RETRAIT_PRODUIT`) : créé/complété
  par le *seed* des modèles au démarrage (`ModeleService`). Visible ensuite dans
  Informations et Campagnes.
- **Référentiel `TOURNEE`** : nouveau type de référentiel géographique. Aucune
  migration ; il utilise la table existante `usp_referentiel_geo`. À alimenter via
  Paramètres → Référentiels → Tournées (ou l'import CSV `code;libellé`).

## 4. Dépendance ajoutée (build)

- **OpenPDF** (`com.github.librepdf:openpdf:1.3.30`) — génération du relevé de compte
  PDF (module Recouvrement). Récupérée par Maven au build ; embarquée dans le WAR
  (`WEB-INF/lib/openpdf-1.3.30.jar`). S'assurer que le serveur de build a accès au
  dépôt Maven central lors de la compilation.

## 5. Paramètres / constantes à vérifier

- **« À propos »** (version, développeur, e-mail) : valeurs en **dur** dans
  `AboutResource.java` (non modifiables via l'écran, par choix). Les mettre à jour
  à chaque livraison si besoin (`VERSION`, `DEVELOPPEUR`, `EMAIL`).
- **`app.societe`** (Paramètres) : sert désormais de valeur par défaut (pré-remplie,
  modifiable) dans les écrans de création (Informations, Disponibilités). Vérifier
  qu'elle est renseignée.
- **`app.url_base`** : requis pour les pièces jointes WhatsApp (URL publique HTTPS
  des médias) — déjà nécessaire pour les médias existants.

## 6. Procédure de déploiement recommandée

1. **Sauvegarde de la base** (dump complet) avant tout — filet de sécurité standard.
2. Construire le WAR : `mvn clean package` (lance aussi les tests unitaires).
3. Déployer le WAR sur Payara et **redémarrer** l'instance.
4. Au premier démarrage : Flyway applique V49 → V51 (vérifier les logs
   `flyway.migrate` : « Successfully applied … migrations »).
5. Contrôles rapides post-déploiement :
   - Comptes clients : colonnes **Code client / Nom client / Entreprise / Téléphone**,
     filtres **Agence / Région** alimentés par les référentiels.
   - Paramètres → Référentiels : onglet **Tournées** présent, import `code;libellé`.
   - Informations / Campagnes : audience **« Un ou plusieurs segments »** (bouton
     ☑ Choisir) ; modèle **Retrait de produit** disponible.
   - « À propos » (barre du haut) affiche version + développeur + e-mail.

## 7. Retour arrière (rollback)

- Les migrations étant **additives**, revenir à l'ancien WAR fonctionne sans toucher
  la base : les colonnes ajoutées (`piece_jointe`, `entreprise`, `segmentation_ids`)
  sont simplement ignorées par l'ancienne version.
- Ne PAS supprimer ces colonnes en cas de rollback (l'ancienne version n'en dépend
  pas). Si un nettoyage est vraiment souhaité plus tard :
  `ALTER TABLE usp_client DROP COLUMN entreprise;` (et équivalents) — à faire hors
  production et après sauvegarde.

## 8. Non-régression

- Le ciblage agence/région/tournée passe d'un filtre `=` à `IN` **de façon
  additive** : une seule valeur = résultat identique à l'ancien comportement
  (couvert par `CampagneCiblageTest`).
- Suite de tests : `mvn test` (JUnit + Mockito), **15 tests** au vert.
