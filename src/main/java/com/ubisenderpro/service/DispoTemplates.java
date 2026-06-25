package com.ubisenderpro.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modèles de message « disponibilité / rupture » prédéfinis (spec §14).
 *
 * <p>Créés en base au démarrage ({@code Bootstrap}) puis éditables dans l'onglet
 * « Modèles de messages » du menu Marketing (agrégateur commun). Variables de
 * contexte ({{liste_produits}}, {{societe}}, {{agence}}, {{date_bulletin}}…)
 * remplies à la validation ; {{nom_contact}} et {{segmentation}} résolus par
 * destinataire à l'envoi.</p>
 */
public final class DispoTemplates {

    private DispoTemplates() {}

    public static final String CLE_DISPONIBILITE = "DISPO_ANNONCE_DISPONIBILITE";
    public static final String CLE_RETOUR_RUPTURE = "DISPO_RETOUR_RUPTURE";
    public static final String CLE_RISQUE_RUPTURE = "DISPO_ALERTE_RISQUE_RUPTURE";
    public static final String CLE_STOCK_LIMITE = "DISPO_ANNONCE_STOCK_LIMITE";
    public static final String CLE_RUPTURE_CONFIRMEE = "DISPO_RUPTURE_CONFIRMEE";

    public static final Map<String, String> NOMS = new LinkedHashMap<>();
    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    public static final Map<String, String> CORPS = new LinkedHashMap<>();

    static {
        NOMS.put(CLE_DISPONIBILITE, "Dispo — Annonce de disponibilité");
        NOMS.put(CLE_RETOUR_RUPTURE, "Dispo — Retour de rupture (prioritaire)");
        NOMS.put(CLE_RISQUE_RUPTURE, "Dispo — Alerte risque de rupture");
        NOMS.put(CLE_STOCK_LIMITE, "Dispo — Stock limité");
        NOMS.put(CLE_RUPTURE_CONFIRMEE, "Dispo — Rupture confirmée");

        TYPES.put(CLE_DISPONIBILITE, "DISPONIBILITE");
        TYPES.put(CLE_RETOUR_RUPTURE, "RETOUR_RUPTURE");
        TYPES.put(CLE_RISQUE_RUPTURE, "RISQUE_RUPTURE");
        TYPES.put(CLE_STOCK_LIMITE, "STOCK_LIMITE");
        TYPES.put(CLE_RUPTURE_CONFIRMEE, "RUPTURE_CONFIRMEE");

        CORPS.put(CLE_DISPONIBILITE,
                "📢🚨 UBI INFO — PRODUITS DISPONIBLES 🚨📢\n\n"
                + "🌟 Bonne nouvelle ! 🌟\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "Nous avons le plaisir de vous informer de la disponibilité des produits suivants :\n\n"
                + "{{liste_produits}}\n\n"
                + "📍 Ces produits sont disponibles dès maintenant auprès de {{societe}} — Agence de {{agence}}.\n\n"
                + "💻 Passez votre commande facilement sur EXTRANET afin de sécuriser votre stock.\n\n"
                + "Les produits sont disponibles dans la limite des quantités en stock.\n\n"
                + "À très bientôt !\n\n"
                + "Direction Commerciale — {{societe}}");

        CORPS.put(CLE_RETOUR_RUPTURE,
                "📢🚨 UBI INFO — RETOUR DE RUPTURE 🚨📢\n\n"
                + "🌟 Bonne nouvelle ! 🌟\n\n"
                + "Bonjour Docteur {{nom_contact}},\n\n"
                + "En votre qualité de client {{segmentation}} de {{societe}}, nous avons le plaisir de vous informer du retour en stock des produits suivants :\n\n"
                + "{{liste_produits}}\n\n"
                + "⚠️ Attention : les quantités disponibles sont limitées.\n\n"
                + "Pour réserver vos quantités, utilisez le lien ci-dessous avant le {{date_limite_reservation}} :\n\n"
                + "👉 {{lien_reservation}}\n\n"
                + "Nous vous remercions pour votre confiance et votre fidélité.\n\n"
                + "🌟 Avec {{societe}}, vous êtes entre de bonnes mains.\n\n"
                + "Direction Commerciale — {{societe}}");

        CORPS.put(CLE_RISQUE_RUPTURE,
                "📢🚨 UBI ALERTE — RISQUE DE RUPTURE PRODUITS 🚨📢\n\n"
                + "Bonjour Docteur {{nom_contact}},\n\n"
                + "En votre qualité de client {{segmentation}} de {{societe}}, nous vous invitons à consulter le bulletin d'information sur les produits présentant un risque de rupture au {{date_bulletin}}.\n\n"
                + "📄 Le document joint contient la liste des produits concernés et les informations utiles pour anticiper vos besoins.\n\n"
                + "Nous vous recommandons d'évaluer votre stock et de préparer vos commandes dans les meilleurs délais.\n\n"
                + "✨ Merci pour votre confiance et votre fidélité.\n\n"
                + "🌟 Avec {{societe}}, vous êtes entre de bonnes mains.\n\n"
                + "Direction Commerciale — {{societe}}");

        CORPS.put(CLE_STOCK_LIMITE,
                "📢🚨 UBI INFO — STOCK LIMITÉ 🚨📢\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "Les produits suivants sont disponibles en quantités limitées :\n\n"
                + "{{liste_produits}}\n\n"
                + "📍 Disponibles auprès de {{societe}} — Agence de {{agence}}, dans la limite des stocks.\n\n"
                + "👉 Pour réserver vos quantités : {{lien_reservation}}\n\n"
                + "💻 Nous vous invitons à passer commande rapidement sur EXTRANET.\n\n"
                + "Direction Commerciale — {{societe}}");

        CORPS.put(CLE_RUPTURE_CONFIRMEE,
                "📢🚨 UBI INFO — RUPTURE DE STOCK 🚨📢\n\n"
                + "Cher(e) client(e) {{nom_contact}},\n\n"
                + "Nous vous informons que les produits suivants sont actuellement en rupture de stock :\n\n"
                + "{{liste_produits}}\n\n"
                + "📅 Retour prévisionnel : {{date_retour}}.\n\n"
                + "Nous mettons tout en œuvre pour un réapprovisionnement dans les meilleurs délais et reviendrons vers vous dès leur disponibilité.\n\n"
                + "Merci de votre compréhension et de votre fidélité.\n\n"
                + "Direction Commerciale — {{societe}}");
    }

    /** Clé système du modèle dispo correspondant à un type d'événement. */
    public static String clePourTypeEvenement(String typeEvenement) {
        if (typeEvenement == null) { return null; }
        switch (typeEvenement) {
            case "ANNONCE_DISPONIBILITE": return CLE_DISPONIBILITE;
            case "RETOUR_RUPTURE":        return CLE_RETOUR_RUPTURE;
            case "RISQUE_RUPTURE":        return CLE_RISQUE_RUPTURE;
            case "STOCK_LIMITE":          return CLE_STOCK_LIMITE;
            case "RUPTURE_CONFIRMEE":     return CLE_RUPTURE_CONFIRMEE;
            default: return null;
        }
    }
}
