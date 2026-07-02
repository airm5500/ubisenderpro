# UbiLicense Manager — outil éditeur (NE PAS LIVRER AU CLIENT)

Application de bureau (Swing) de génération des licences **UbiSmartCRM Pro**.
Elle détient la **clé privée** : elle reste chez l'éditeur, hors du WAR livré.

## Build & lancement

```bash
mvn -f tools/ubilicense-manager/pom.xml package
java -jar tools/ubilicense-manager/target/ubilicense-manager.jar
```

(Projet Maven autonome : il n'affecte pas le build du WAR principal.)

## Procédure d'activation hors ligne

1. **Client** : menu Licence → « Générer une demande (.licreq) » → il vous
   transmet le fichier `REQUEST.licreq`.
2. **Éditeur** : onglet 🔑 Licence → « Charger .licreq… » (remplit l'empreinte
   serveur), compléter identité / dates / modules → « Générer la licence » →
   fichier `LICENSE-XXX.lic` (ou clé d'activation affichée, équivalente).
3. **Client** : menu Licence → « Importer une licence » → colle la clé ou
   charge le `.lic` → activation immédiate (signature vérifiée localement).

Renouvellement = même procédure (nouvel import, aucune réinstallation).
Transfert de serveur = nouvelle demande `.licreq` depuis le nouveau serveur,
puis émission d'une nouvelle licence.

## Clés

- `cles/private_DEV.pem` : paire de **DÉVELOPPEMENT** appariée à la clé
  publique embarquée dans l'application (`src/main/resources/licence/public.pem`).
  **Pour la production** : onglet 🗝️ Clés → générer une nouvelle paire, stocker
  `private.pem` hors dépôt (coffre / support chiffré, sauvegardé), copier
  `public.pem` dans `src/main/resources/licence/public.pem` puis rebuilder le WAR.
- ⚠️ La **fuite de la clé privée compromet tout le système** ; sa **perte**
  empêche d'émettre de nouvelles licences (les licences déjà émises restent
  valides).
- ⚠️ Régénérer les clés invalide les licences émises avec l'ancienne paire.

## Côté application (rappel)

- `licence.obligatoire=false` (défaut) : aucun blocage, bandeau informatif.
  Passer à `true` (Paramètres) pour un déploiement commercial.
- Après expiration + grâce (`licence.grace_jours`, défaut 7) : envois,
  automatisations et imports bloqués ; consultation, écran Licence et rôle
  SUPPORT (mode maintenance) toujours accessibles.
