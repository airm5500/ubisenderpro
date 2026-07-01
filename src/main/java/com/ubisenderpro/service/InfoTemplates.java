package com.ubisenderpro.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modèles de message « informations clients / alertes opérationnelles » (§12).
 * Créés en base au démarrage puis éditables dans l'onglet « Modèles de messages »
 * de Marketing. Le contexte ({{agence}}, {{jour_ferie}}, {{message}}…) est rempli
 * à la validation ; {{nom_contact}}, {{civilite_complete}}, {{nom_compte}} sont
 * résolus par destinataire à l'envoi. Les lignes à variable facultative vide
 * sont supprimées proprement.
 */
public final class InfoTemplates {

    private InfoTemplates() {}

    public static final Map<String, String> NOMS = new LinkedHashMap<>();
    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    public static final Map<String, String> CORPS = new LinkedHashMap<>();

    private static void t(String cle, String nom, String type, String corps) {
        NOMS.put(cle, nom); TYPES.put(cle, type); CORPS.put(cle, corps);
    }

    static {
        t("INFO_RETARD_LIVRAISON", "Info — Retard de livraison", "RETARD_LIVRAISON",
                "📢 *UBI INFO — LIVRAISON*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Nous rencontrons actuellement un retard sur certaines livraisons concernant *{{agence_ou_tournee}}*.\n\n"
                + "Nos équipes sont pleinement mobilisées afin de vous livrer dans les meilleurs délais.\n\n"
                + "{{nouvelle_estimation_livraison}}\n\n"
                + "Nous vous présentons nos sincères excuses pour ce contretemps et vous remercions pour votre patience et votre compréhension.\n\n"
                + "*Merci pour votre confiance.*\n\n"
                + "*Direction {{agence}}*");

        t("INFO_MISE_A_JOUR_LIVRAISON", "Info — Mise à jour livraison", "MODIFICATION_TOURNEE",
                "📢 *UBI INFO — MISE À JOUR LIVRAISON*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Mise à jour concernant la livraison sur *{{agence_ou_tournee}}*.\n\n"
                + "{{nouvelle_estimation_livraison}}\n\n"
                + "Nous restons mobilisés et vous tiendrons informé(e) de toute évolution.\n\n"
                + "*Merci pour votre confiance.*\n\n"
                + "*Direction {{agence}}*");

        t("INFO_REPRISE_LIVRAISON", "Info — Reprise des livraisons", "REPRISE_LIVRAISON",
                "📢 *UBI INFO — REPRISE DES LIVRAISONS*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Bonne nouvelle : les livraisons sur *{{agence_ou_tournee}}* reprennent normalement.\n\n"
                + "Nous vous remercions pour votre patience et votre compréhension.\n\n"
                + "💻 Vous pouvez transmettre vos commandes via *EXTRANET*.\n\n"
                + "*Merci pour votre confiance.*\n\n"
                + "*Direction {{agence}}*");

        t("INFO_ANNULATION_TOURNEE", "Info — Annulation de tournée", "ANNULATION_TOURNEE",
                "📢 *UBI INFO — TOURNÉE*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Nous vous informons de l'annulation de la tournée *{{tournee}}*.\n\n"
                + "Nos équipes reviennent vers vous dans les meilleurs délais pour la reprogrammation.\n\n"
                + "Nous vous présentons nos excuses pour la gêne occasionnée.\n\n"
                + "*Direction {{agence}}*");

        t("INFO_INFORMATION_GARDE", "Info — Information de garde", "INFORMATION_GARDE",
                "📢 *RAPPEL — INFORMATION DE GARDE*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "À l'occasion de *{{jour_ferie}}*, nous vous invitons à transmettre vos commandes à l'agence de *{{agence}}* avant *{{heure_limite_commande}}*.\n\n"
                + "📦 Modalités de livraison : *{{consignes_livraison}}*\n\n"
                + "💻 Merci de privilégier la saisie de vos commandes via *EXTRANET*.\n\n"
                + "Toute l'équipe de *{{societe}}* vous souhaite une excellente garde et une bonne fête.\n\n"
                + "Pharmacien de garde : *{{pharmacien_garde}}*\n"
                + "📞 *{{telephone_pharmacien}}*\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_RAPPEL_GARDE", "Info — Rappel de garde", "INFORMATION_GARDE",
                "📢 *RAPPEL — GARDE À VENIR*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Rappel : à l'occasion de *{{jour_ferie}}*, pensez à transmettre vos commandes à l'agence de *{{agence}}* avant *{{heure_limite_commande}}*.\n\n"
                + "💻 Privilégiez *EXTRANET* pour la saisie.\n\n"
                + "Pharmacien de garde : *{{pharmacien_garde}}*\n"
                + "📞 *{{telephone_pharmacien}}*\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_INFORMATION_JOUR_FERIE", "Info — Jour férié", "INFORMATION_JOUR_FERIE",
                "📢 *UBI INFO — JOUR FÉRIÉ*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "À l'occasion de *{{jour_ferie}}*, l'organisation des commandes et livraisons est adaptée.\n\n"
                + "📦 Modalités : *{{consignes_livraison}}*\n\n"
                + "💻 Merci de transmettre vos commandes via *EXTRANET* avant *{{heure_limite_commande}}*.\n\n"
                + "Toute l'équipe de *{{societe}}* vous remercie.\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_MODIFICATION_HORAIRES", "Info — Modification des horaires", "MODIFICATION_HORAIRES",
                "📢 *UBI INFO — HORAIRES*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Nous vous informons d'une modification des horaires de l'agence de *{{agence}}*.\n\n"
                + "{{message}}\n\n"
                + "Merci de votre compréhension.\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_FERMETURE_AGENCE", "Info — Fermeture d'agence", "FERMETURE_AGENCE",
                "📢 *UBI INFO — FERMETURE*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "L'agence de *{{agence}}* sera fermée.\n\n"
                + "{{message}}\n\n"
                + "💻 Vous pouvez continuer à transmettre vos commandes via *EXTRANET*.\n\n"
                + "Merci de votre compréhension.\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_INFORMATION_GENERALE", "Info — Information générale", "INFORMATION_GENERALE",
                "📢 *UBI INFO*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "{{message}}\n\n"
                + "*Merci pour votre confiance.*\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_ALERTE_URGENTE", "Info — Alerte urgente", "ALERTE_URGENTE",
                "🚨 *UBI ALERTE*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "{{message}}\n\n"
                + "Merci de votre attention.\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_RETRAIT_PRODUIT", "Info — Retrait de produit", "RETRAIT_PRODUIT",
                "⚠️ *UBI INFO — RETRAIT DE PRODUIT*\n\n"
                + "Cher(e) client(e) *{{nom_contact}}*,\n\n"
                + "Nous vous informons du *retrait* du produit suivant :\n"
                + "*{{message}}*\n\n"
                + "Par mesure de précaution, nous vous invitons à *cesser toute dispensation* de ce produit et à *isoler les unités* encore en votre possession.\n\n"
                + "Nos équipes reviennent vers vous pour les modalités de retour ou d'échange.\n\n"
                + "Merci pour votre vigilance et votre confiance.\n\n"
                + "*{{direction_signataire}}*");

        t("INFO_ANNIVERSAIRE_CLIENT", "Info — Anniversaire client", "ANNIVERSAIRE_CLIENT",
                "🎉 *Joyeux anniversaire !* 🎉\n\n"
                + "*{{civilite_complete}}*,\n\n"
                + "En ce jour spécial, toute l'équipe de *{{societe}}* tient à vous souhaiter un très bel anniversaire ! 🎂\n\n"
                + "Que cette journée vous apporte bonheur, santé et réussite, aussi bien sur le plan personnel que professionnel.\n\n"
                + "Merci pour votre confiance et pour ce précieux partenariat. 🙏\n\n"
                + "Profitez pleinement de votre journée ! 🥂\n\n"
                + "Au plaisir de vous accompagner encore longtemps.\n\n"
                + "*L'équipe {{societe}}*");
    }

    /** Clé système du modèle info correspondant à un type d'information. */
    public static String clePourType(String typeInfo) {
        if (typeInfo == null) { return null; }
        switch (typeInfo) {
            case "RETARD_LIVRAISON": return "INFO_RETARD_LIVRAISON";
            case "MODIFICATION_TOURNEE": return "INFO_MISE_A_JOUR_LIVRAISON";
            case "ANNULATION_TOURNEE": return "INFO_ANNULATION_TOURNEE";
            case "REPRISE_LIVRAISON": return "INFO_REPRISE_LIVRAISON";
            case "INFORMATION_GARDE": return "INFO_INFORMATION_GARDE";
            case "INFORMATION_JOUR_FERIE": return "INFO_INFORMATION_JOUR_FERIE";
            case "MODIFICATION_HORAIRES": return "INFO_MODIFICATION_HORAIRES";
            case "FERMETURE_AGENCE": return "INFO_FERMETURE_AGENCE";
            case "INFORMATION_GENERALE": return "INFO_INFORMATION_GENERALE";
            case "ALERTE_URGENTE": return "INFO_ALERTE_URGENTE";
            case "RETRAIT_PRODUIT": return "INFO_RETRAIT_PRODUIT";
            case "ANNIVERSAIRE_CLIENT": return "INFO_ANNIVERSAIRE_CLIENT";
            default: return null;
        }
    }
}
