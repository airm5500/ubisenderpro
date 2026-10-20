package ci.ubipharm.crm.rest.service.impl;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lecture du journal des actions pour un humain : type de donnee (Client, Facture...) et nature
 * (Creation, Modification...) deduits du code d'action, en Java pour les libelles et en SQL pour les filtres.
 * Les motifs sont des constantes de ce fichier : ils sont ecrits tels quels dans la requete.
 */
final class ClassementJournal {

    private ClassementJournal() {
    }

    record Type(String code, String libelle, List<String> prefixes) {
    }

    /** Ordre d'affichage de la liste deroulante ; un prefixe plus long l'emporte sur un plus court (CAMPAGNE_MARKETING_ avant CAMPAGNE_). */
    static final List<Type> TYPES = List.of(
            new Type("CLIENT", "Client", List.of("CLIENT_")),
            new Type("FACTURE", "Facture", List.of("FACTURE_")),
            new Type("PORTEFEUILLE", "Portefeuille et délégation", List.of("AFFECTATION", "DELEGATION_")),
            new Type("UTILISATEUR", "Utilisateur", List.of("UTILISATEUR_", "MOT_DE_PASSE_")),
            new Type("SESSION", "Connexion à l'application", List.of("CONNEXION")),
            new Type("ROLE", "Rôle et droits", List.of("ROLE_")),
            new Type("AGENCE", "Agence", List.of("AGENCE_")),
            new Type("REFERENTIEL", "Référentiel et segment", List.of("REFERENTIEL_", "SEGMENT_")),
            new Type("PARAMETRE", "Paramètre et charte", List.of("PARAMETRE_", "CHARTE_")),
            new Type("DELAI_RELANCE", "Délai de relance", List.of("DELAI_RELANCE_")),
            new Type("MODELE", "Modèle de message et variable", List.of("MODELE_", "VARIABLE_")),
            new Type("CAMPAGNE", "Campagne de relance", List.of("CAMPAGNE_")),
            new Type("CAMPAGNE_MARKETING", "Campagne promotionnelle", List.of("CAMPAGNE_MARKETING_")),
            new Type("LISTE", "Liste de diffusion", List.of("LISTE_")),
            new Type("PRODUIT", "Produit", List.of("PRODUIT_")),
            new Type("PROMOTION", "Promotion", List.of("PROMOTION_")),
            new Type("INFORMATION", "Information client", List.of("INFORMATION_")),
            new Type("EVENEMENT", "Événement", List.of("EVENEMENT_")),
            new Type("WHATSAPP", "Numéro WhatsApp", List.of("WHATSAPP_", "AGENT_NUMERO", "META_")),
            new Type("FOURNISSEUR_SMS", "Fournisseur SMS", List.of("FOURNISSEUR_SMS_")),
            new Type("ENVOI", "Envoi en masse et discussion", List.of("ENVOI_MASSE_", "DISCUSSION_")),
            new Type("IMPORT", "Import", List.of("IMPORT_")),
            new Type("PIECE_JOINTE", "Pièce jointe", List.of("PIECE_JOINTE_")),
            new Type("SUPPORT", "Centre de support", List.of("SUPPORT_")),
            new Type("JOURNAL", "Journal des actions", List.of("JOURNAL_")),
            new Type("AUTRE", "Autre", List.of()));

    /** Nature d'une action : motif SQL LIKE sur le code, le premier qui correspond l'emporte. */
    private record Regle(String motif, String nature) {
    }

    static final Map<String, String> NATURES = libelles(
            "CREATION", "Création",
            "MODIFICATION", "Modification",
            "DESACTIVATION", "Désactivation / archivage",
            "REACTIVATION", "Réactivation",
            "CONNEXION", "Connexion",
            "ENVOI", "Envoi",
            "AUTRE", "Autre");

    private static final List<Regle> REGLES = List.of(
            // Connexions (application et numeros WhatsApp) avant le reste
            new Regle("CONNEXION%", "CONNEXION"),
            new Regle("WHATSAPP\\_CONNEXION", "CONNEXION"),
            new Regle("WHATSAPP\\_DECONNEXION", "CONNEXION"),
            // Envois : lancement de campagne, envoi en masse, reponse dans une discussion, SMS d'essai
            new Regle("%\\_LANCEMENT", "ENVOI"),
            new Regle("ENVOI\\_MASSE\\_CREATION", "ENVOI"),
            new Regle("DISCUSSION\\_REPONSE", "ENVOI"),
            new Regle("FOURNISSEUR\\_SMS\\_ESSAI", "ENVOI"),
            new Regle("%\\_REACTIVATION", "REACTIVATION"),
            new Regle("%\\_REABONNEMENT", "REACTIVATION"),
            new Regle("%\\_ACTIVATION", "REACTIVATION"),
            new Regle("%\\_DESACTIVATION", "DESACTIVATION"),
            new Regle("%\\_ARCHIVAGE", "DESACTIVATION"),
            new Regle("%\\_ANNULATION", "DESACTIVATION"),
            new Regle("%\\_RETRAIT", "DESACTIVATION"),
            new Regle("%\\_DESABONNEMENT", "DESACTIVATION"),
            new Regle("%\\_CREATION", "CREATION"),
            new Regle("%\\_AJOUT", "CREATION"),
            new Regle("%\\_DUPLICATION", "CREATION"),
            new Regle("%\\_DEPOT", "CREATION"),
            new Regle("IMPORT\\_MODELE\\_ENREGISTREMENT", "CREATION"),
            new Regle("IMPORT\\_VALIDATION", "CREATION"),
            new Regle("%\\_MODIFICATION", "MODIFICATION"),
            new Regle("%\\_SUIVI", "MODIFICATION"),
            new Regle("%\\_STATUT", "MODIFICATION"),
            new Regle("%\\_AFFECTATION", "MODIFICATION"),
            new Regle("AFFECTATION", "MODIFICATION"),
            new Regle("%\\_DEFAUT", "MODIFICATION"),
            new Regle("%\\_CORRECTION", "MODIFICATION"),
            new Regle("%\\_RUPTURE", "MODIFICATION"),
            new Regle("%\\_RETOUR\\_STOCK", "MODIFICATION"),
            new Regle("%\\_PASSAGE\\_%", "MODIFICATION"),
            new Regle("%\\_REINITIALISATION\\_MDP", "MODIFICATION"),
            new Regle("MOT\\_DE\\_PASSE\\_CHANGEMENT", "MODIFICATION"),
            new Regle("%\\_SOUMISSION", "MODIFICATION"),
            new Regle("%\\_VALIDATION", "MODIFICATION"),
            new Regle("%\\_REJET", "MODIFICATION"),
            new Regle("%\\_TRAITEMENT", "MODIFICATION"),
            new Regle("%\\_VERIFICATION%", "MODIFICATION"),
            new Regle("LISTE\\_CLIENTS", "MODIFICATION"),
            new Regle("AGENT\\_NUMERO", "MODIFICATION"));

    /** Libelles des codes d'action les plus courants ; les autres sont rendus lisibles a partir du code. */
    static final Map<String, String> ACTIONS = libelles(
            "CONNEXION", "Connexion réussie",
            "CONNEXION_ECHEC", "Connexion refusée",
            "AFFECTATION", "Clients confiés à un agent",
            "AFFECTATION_RETRAIT", "Clients retirés du portefeuille",
            "AGENT_NUMERO", "Numéro WhatsApp de l'agent",
            "CLIENT_DESABONNEMENT", "Client désabonné",
            "CLIENT_REABONNEMENT", "Client réabonné",
            "CLIENT_VERIFICATION_NUMEROS", "Vérification des numéros WhatsApp",
            "FACTURE_SUIVI", "Suivi de facture",
            "MOT_DE_PASSE_CHANGEMENT", "Mot de passe changé",
            "UTILISATEUR_REINITIALISATION_MDP", "Mot de passe réinitialisé",
            "PRODUIT_RUPTURE", "Produit en rupture",
            "PRODUIT_RETOUR_STOCK", "Produit de retour en stock",
            "PROMOTION_DEBUT", "Début de promotion",
            "PROMOTION_FIN", "Fin de promotion",
            "LISTE_CLIENTS", "Clients de la liste",
            "DISCUSSION_REPONSE", "Réponse dans une discussion",
            "FOURNISSEUR_SMS_DEFAUT", "Fournisseur SMS par défaut",
            "FOURNISSEUR_SMS_ESSAI", "SMS d'essai",
            "IMPORT_SIMULATION", "Fichier d'import chargé",
            "IMPORT_VALIDATION", "Import validé",
            "IMPORT_CORRECTION", "Ligne d'import corrigée",
            "IMPORT_MODELE_ENREGISTREMENT", "Modèle d'import enregistré",
            "META_COMPTE_MODIFICATION", "Compte API Meta modifié",
            "META_JETON_VERIFICATION", "Jeton du webhook renouvelé",
            "WHATSAPP_CONNEXION", "Numéro WhatsApp relié",
            "WHATSAPP_DECONNEXION", "Numéro WhatsApp délié",
            "WHATSAPP_PASSAGE_META", "Passage à l'API Meta",
            "WHATSAPP_PASSAGE_WA_WEB", "Passage à WhatsApp Web",
            "PIECE_JOINTE_DEPOT", "Affiche déposée",
            "SUPPORT_ERREUR_TRAITEMENT", "Erreur marquée traitée",
            "SUPPORT_ERREUR_PURGE", "Purge du diagnostic",
            "SUPPORT_TICKET_MESSAGE", "Message sur un ticket",
            "JOURNAL_EXPORT", "Export du journal");

    private static final Map<String, String> OPERATIONS = libelles(
            "CREATION", "Création", "MODIFICATION", "Modification", "ARCHIVAGE", "Archivage", "REACTIVATION", "Réactivation",
            "DESACTIVATION", "Désactivation", "ACTIVATION", "Activation", "ANNULATION", "Annulation", "DUPLICATION", "Duplication",
            "SOUMISSION", "Soumission à validation", "VALIDATION", "Validation", "REJET", "Rejet", "LANCEMENT", "Lancement",
            "STATUT", "Changement de statut", "AFFECTATION", "Affectation", "AJOUT", "Ajout", "RETRAIT", "Retrait");

    /** Sous-objet precise quand le type de donnee ne suffit pas (PROMOTION_PRODUIT_AJOUT : produit de la promotion). */
    private static final Map<String, String> SOUS_OBJETS = libelles(
            "PROMOTION_PRODUIT_", "produit de la promotion",
            "IMPORT_MODELE_", "modèle d'import",
            "SEGMENT_", "segment",
            "VARIABLE_", "variable de message",
            "CHARTE_", "logo ou icône",
            "PARAMETRE_AIDE_", "texte d'aide",
            "DELEGATION_", "délégation",
            "SUPPORT_FAQ_", "question de la FAQ",
            "SUPPORT_TICKET_", "ticket",
            "ENVOI_MASSE_", "envoi en masse");

    private static final List<String> PREFIXES_TRIES = TYPES.stream().flatMap(t -> t.prefixes().stream())
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private static final Map<String, String> TYPE_DU_PREFIXE = TYPES.stream()
            .flatMap(t -> t.prefixes().stream().map(p -> Map.entry(p, t.code())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Type> TYPES_PAR_CODE = TYPES.stream().collect(Collectors.toMap(Type::code, t -> t));

    /** Expression SQL du type de donnee d'une ligne d'alias j. */
    static String sqlType(String alias) {
        StringBuilder sql = new StringBuilder("(case");
        for (String prefixe : PREFIXES_TRIES) {
            sql.append(" when ").append(alias).append(".action like '").append(prefixe.replace("_", "\\_")).append("%' then '")
                    .append(TYPE_DU_PREFIXE.get(prefixe)).append("'");
        }
        return sql.append(" else 'AUTRE' end)").toString();
    }

    /** Expression SQL de la nature d'une ligne d'alias j. */
    static String sqlNature(String alias) {
        StringBuilder sql = new StringBuilder("(case");
        for (Regle regle : REGLES) {
            sql.append(" when ").append(alias).append(".action like '").append(regle.motif()).append("' then '").append(regle.nature()).append("'");
        }
        return sql.append(" else 'AUTRE' end)").toString();
    }

    static boolean typeConnu(String code) {
        return TYPES_PAR_CODE.containsKey(code);
    }

    static String libelleType(String code) {
        Type type = TYPES_PAR_CODE.get(code);
        return type == null ? "Autre" : type.libelle();
    }

    static String libelleNature(String code) {
        return NATURES.getOrDefault(code, "Autre");
    }

    /** Libelle de l'operation : propre au code, sinon deduit de son suffixe (CREATION : Création), avec le sous-objet s'il y a lieu. */
    static String libelleAction(String action) {
        if (action == null) {
            return "";
        }
        String connu = ACTIONS.get(action);
        if (connu != null) {
            return connu;
        }
        String suffixe = action.contains("_") ? action.substring(action.lastIndexOf('_') + 1) : action;
        String operation = OPERATIONS.get(suffixe);
        if (operation == null) {
            String texte = action.replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
        }
        return SOUS_OBJETS.entrySet().stream().filter(e -> action.startsWith(e.getKey())).findFirst()
                .map(e -> operation + " (" + e.getValue() + ")").orElse(operation);
    }

    private static Map<String, String> libelles(String... paires) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            map.put(paires[i], paires[i + 1]);
        }
        return map;
    }
}
