package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ecarts « champ : ancienne -> nouvelle valeur » d'une modification, pour le detail du journal des actions.
 * Seuls les champs reellement changes sont retenus ; une valeur vide s'ecrit « vide ».
 */
final class EcartsJournal {

    private static final int LONGUEUR_MAX = 120;

    private final List<String> ecarts = new ArrayList<>();

    EcartsJournal champ(String libelle, Object avant, Object apres) {
        String a = texte(avant);
        String b = texte(apres);
        if (!Objects.equals(a, b)) {
            ecarts.add(libelle + " : " + (a == null ? "vide" : a) + " -> " + (b == null ? "vide" : b));
        }
        return this;
    }

    /** Champ sensible ou trop long : on signale le changement sans recopier la valeur. */
    EcartsJournal masque(String libelle, Object avant, Object apres) {
        if (!Objects.equals(texte(avant), texte(apres))) {
            ecarts.add(libelle + " modifié");
        }
        return this;
    }

    boolean estVide() {
        return ecarts.isEmpty();
    }

    /** « ref : ecart ; ecart » ou « ref : aucun changement ». */
    String detail(String reference) {
        return reference + " : " + (ecarts.isEmpty() ? "aucun changement" : String.join(" ; ", ecarts));
    }

    private static String texte(Object valeur) {
        if (valeur == null) {
            return null;
        }
        String t = valeur instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString()
                : valeur instanceof Boolean b ? (b ? "oui" : "non") : valeur.toString().trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > LONGUEUR_MAX ? t.substring(0, LONGUEUR_MAX) + "…" : t;
    }
}
