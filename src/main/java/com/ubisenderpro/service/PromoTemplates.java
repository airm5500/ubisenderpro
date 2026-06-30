package com.ubisenderpro.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modèles de message « promotion » prédéfinis.
 *
 * <p>Sont la source de référence des textes des 4 modèles (annonce mensuelle,
 * lancement, derniers jours, dernière chance). Ils sont créés en base au
 * démarrage ({@code Bootstrap}) puis deviennent éditables dans l'onglet
 * « Modèles de promo ». La génération lit le corps depuis la base (clé système)
 * et retombe sur ces textes par défaut si le modèle a été supprimé.</p>
 *
 * <p>Variables : le contexte ({{nom_promotion}}, {{date_fin}}, {{avantage_ug}}…)
 * est rempli à la génération/validation ; {{nom_contact}} est résolu par
 * destinataire à l'envoi. {{avantage_ug}} vaut « jusqu'à X % d'unités gratuites »
 * ou « des unités gratuites » : il n'est jamais vide (anti-variable-vide).</p>
 */
public final class PromoTemplates {

    private PromoTemplates() {}

    public static final String CLE_ANNONCE = "PROMO_ANNONCE_MENSUELLE";
    public static final String CLE_LANCEMENT = "PROMO_LANCEMENT";
    public static final String CLE_RAPPEL_FIN = "PROMO_RAPPEL_FIN";
    public static final String CLE_DERNIERE_CHANCE = "PROMO_DERNIERE_CHANCE";

    /** Libellés (nom affiché) des modèles prédéfinis. */
    public static final Map<String, String> NOMS = new LinkedHashMap<>();
    /** Type de modèle (tous PROMOTION ici). */
    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    /** Corps par défaut des modèles prédéfinis. */
    public static final Map<String, String> CORPS = new LinkedHashMap<>();

    static {
        NOMS.put(CLE_ANNONCE, "Promo — Annonce mensuelle");
        NOMS.put(CLE_LANCEMENT, "Promo — Lancement");
        NOMS.put(CLE_RAPPEL_FIN, "Promo — Derniers jours (J-3)");
        NOMS.put(CLE_DERNIERE_CHANCE, "Promo — Dernière chance (J-1)");

        for (String c : NOMS.keySet()) { TYPES.put(c, "PROMOTION"); }

        CORPS.put(CLE_ANNONCE,
                "📢🔥 PROMOTIONS UG DU MOIS DE {{mois_promotion}} 🔥📢\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "🎉 Découvrez nos offres promotionnelles disponibles durant le mois de {{mois_promotion}}.\n\n"
                + "💥 Bénéficiez de {{avantage_ug}} sur une sélection de produits.\n\n"
                + "📅 Période des offres : du {{date_debut_globale}} au {{date_fin_globale}}.\n\n"
                + "📦 Les offres et les stocks d'unités gratuites sont disponibles dans la limite des quantités prévues.\n\n"
                + "📎 Consultez le fichier Excel joint pour découvrir les produits concernés et préparer facilement votre commande sur EXTRANET.\n\n"
                + "💻 Profitez de ces offres dès maintenant !\n\n"
                + "Direction Commerciale");

        CORPS.put(CLE_LANCEMENT,
                "🚀🎁 NOUVELLE PROMOTION UG 🎁🚀\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "La promotion {{nom_promotion}} commence aujourd'hui !\n\n"
                + "📅 Offre valable du {{date_debut}} au {{date_fin}}.\n\n"
                + "💥 Bénéficiez de {{avantage_ug}} sur {{nombre_produits}} produit(s) sélectionné(s).\n\n"
                + "📎 Consultez le fichier Excel joint pour découvrir les produits et leurs conditions promotionnelles.\n\n"
                + "💻 Passez votre commande via EXTRANET pendant la période de validité de l'offre.\n\n"
                + "Direction Commerciale");

        CORPS.put(CLE_RAPPEL_FIN,
                "✨⏳ PROMOTION UG — DERNIERS JOURS ⏳✨\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "L'offre d'unités gratuites « {{nom_promotion}} » arrive bientôt à expiration.\n\n"
                + "📅 Date de fin : {{date_fin}} — il reste {{jours_restants}} jour(s).\n\n"
                + "🎁 Profitez encore de {{avantage_ug}} sur {{nombre_produits}} produit(s).\n\n"
                + "📎 Le fichier Excel joint présente les produits concernés et leurs conditions.\n\n"
                + "💻 Préparez votre commande et transmettez-la dès maintenant via EXTRANET.\n\n"
                + "Ne laissez pas passer ces dernières opportunités !\n\n"
                + "Direction Commerciale");

        CORPS.put(CLE_DERNIERE_CHANCE,
                "⏰🔥 DERNIÈRE CHANCE — PROMOTION UG 🔥⏰\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "La promotion {{nom_promotion}} prend fin le {{date_fin}}.\n\n"
                + "Il ne vous reste plus que {{jours_restants}} jour(s) pour bénéficier de {{avantage_ug}} sur les produits concernés.\n\n"
                + "📎 Consultez le fichier Excel joint et finalisez votre commande sur EXTRANET.\n\n"
                + "Après cette date, les conditions promotionnelles ne seront plus applicables.\n\n"
                + "Direction Commerciale");
    }

    /** Clé système du modèle à utiliser pour un type de proposition. */
    public static String clePourType(String type) {
        if (type == null) { return null; }
        if ("ANNONCE_MENSUELLE".equals(type)) { return CLE_ANNONCE; }
        if ("LANCEMENT".equals(type)) { return CLE_LANCEMENT; }
        if (type.startsWith("RAPPEL_J")) {
            // J-1 -> dernière chance ; sinon (J-3) -> derniers jours.
            return "RAPPEL_J1".equals(type) ? CLE_DERNIERE_CHANCE : CLE_RAPPEL_FIN;
        }
        return null;
    }
}
