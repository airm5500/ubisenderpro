package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.EntityManager;

/** Avantage d'un produit en promotion : ses controles, communs a la saisie et a l'import, et son texte dans les messages. */
final class AvantagePromotion {

    static final String UG_QUANTITE = "UG_QUANTITE";
    static final String UG_POURCENTAGE = "UG_POURCENTAGE";
    static final String PRIX = "PRIX";

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private AvantagePromotion() {
    }

    /** Avantage d'une ligne : seules les valeurs utiles a son type sont gardees. */
    record Avantage(String type, int quantiteMin, Integer ugQuantite, BigDecimal ugPourcentage, Integer ugPlafond, BigDecimal prixPromo) {

        static Avantage de(String type, int quantiteMin, Integer ugQuantite, BigDecimal ugPourcentage, Integer ugPlafond, BigDecimal prixPromo) {
            return switch (type == null ? "" : type) {
                case UG_QUANTITE -> new Avantage(type, quantiteMin, ugQuantite, null, null, null);
                case UG_POURCENTAGE -> new Avantage(type, quantiteMin, null, ugPourcentage, ugPlafond, null);
                case PRIX -> new Avantage(type, quantiteMin, null, null, null, prixPromo);
                default -> new Avantage(type, quantiteMin, null, null, null, null);
            };
        }
    }

    static List<String> controler(Avantage a, BigDecimal prixPublic) {
        List<String> motifs = new ArrayList<>();
        if (a.quantiteMin() < 1) {
            motifs.add("La quantité minimale est d'au moins 1.");
        }
        switch (a.type() == null ? "" : a.type()) {
            case UG_QUANTITE -> {
                if (a.ugQuantite() == null || a.ugQuantite() < 1) {
                    motifs.add("Indiquez le nombre d'unités offertes.");
                }
            }
            case UG_POURCENTAGE -> {
                if (a.ugPourcentage() == null || a.ugPourcentage().signum() <= 0 || a.ugPourcentage().compareTo(CENT) > 0) {
                    motifs.add("Le pourcentage d'unités gratuites est compris entre 0 et 100.");
                }
                if (a.ugPlafond() != null && a.ugPlafond() < 1) {
                    motifs.add("Le plafond est d'au moins une unité.");
                }
            }
            case PRIX -> {
                if (a.prixPromo() == null || a.prixPromo().signum() <= 0) {
                    motifs.add("Indiquez le prix promotionnel.");
                } else if (prixPublic != null && a.prixPromo().compareTo(prixPublic) >= 0) {
                    motifs.add("Le prix promotionnel (" + prix(a.prixPromo()) + " F) n'est pas inférieur au prix public (" + prix(prixPublic) + " F).");
                }
            }
            default -> motifs.add("Choisissez l'avantage : unités gratuites ou prix promotionnel.");
        }
        return motifs;
    }

    /** « 2 offerts pour 10 achetés », « 10 % d'unités gratuites dès 20 achetés, 5 au plus », « 1 100 F au lieu de 1 250 F ». */
    static String resume(Avantage a, BigDecimal prixPublic) {
        String des = a.quantiteMin() > 1 ? " dès " + a.quantiteMin() + " achetés" : "";
        return switch (a.type()) {
            case UG_QUANTITE -> a.ugQuantite() + (a.ugQuantite() > 1 ? " offerts" : " offert") + " pour " + a.quantiteMin()
                    + (a.quantiteMin() > 1 ? " achetés" : " acheté");
            case UG_POURCENTAGE -> nombre(a.ugPourcentage()) + " % d'unités gratuites" + des
                    + (a.ugPlafond() == null ? "" : ", " + a.ugPlafond() + " au plus");
            case PRIX -> prix(a.prixPromo()) + " F" + (prixPublic == null ? "" : " au lieu de " + prix(prixPublic) + " F") + des;
            default -> "";
        };
    }

    /** Ecrit l'avantage d'une ligne (saisie ou import) et la remet dans la promotion. */
    static void ecrire(EntityManager em, Long ligneId, Avantage avantage, Long importId, Long utilisateurId) {
        em.createNativeQuery("""
                update crm_promotion_produit set quantite_min = :qte, avantage = :avantage, ug_quantite = :ugq, ug_pourcentage = :ugp,
                       ug_plafond = :plafond, prix_promo = :prix, actif = true, import_id = coalesce(:importId, import_id),
                       modifie_par = :par, updated_at = utc_timestamp()
                where id = :id
                """)
                .setParameter("qte", avantage.quantiteMin()).setParameter("avantage", avantage.type())
                .setParameter("ugq", avantage.ugQuantite()).setParameter("ugp", avantage.ugPourcentage())
                .setParameter("plafond", avantage.ugPlafond()).setParameter("prix", avantage.prixPromo())
                .setParameter("importId", importId).setParameter("par", utilisateurId).setParameter("id", ligneId)
                .executeUpdate();
    }

    static String prix(BigDecimal montant) {
        return nombre(montant);
    }

    private static String nombre(BigDecimal valeur) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMaximumFractionDigits(2);
        // Espaces ordinaires : WhatsApp et les SMS affichent mal les espaces insecables
        return format.format(valeur).replace(' ', ' ').replace(' ', ' ');
    }
}
