package ci.ubipharm.crm.recouvrement.rest.service.impl;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Texte d'un modele : les variables s'ecrivent {code}, comme dans UbiSenderPro. */
final class GabaritMessage {

    static final Set<String> TYPES_CAMPAGNE = Set.of("AVANT_ECHEANCE", "JOUR_ECHEANCE", "APRES_ECHEANCE", "PERSONNALISEE");

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z][a-z0-9_]*)\\}");

    private GabaritMessage() {
    }

    /** Codes des variables citees, dans l'ordre d'apparition. */
    static Set<String> variables(String contenu) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = VARIABLE.matcher(contenu);
        while (m.find()) {
            codes.add(m.group(1));
        }
        return codes;
    }

    /** Remplace chaque {code} par sa valeur ; une variable sans valeur devient vide. */
    static String rendre(String contenu, Map<String, String> valeurs) {
        Matcher m = VARIABLE.matcher(contenu);
        StringBuilder resultat = new StringBuilder();
        while (m.find()) {
            String valeur = valeurs.getOrDefault(m.group(1), "");
            m.appendReplacement(resultat, Matcher.quoteReplacement(valeur == null ? "" : valeur));
        }
        m.appendTail(resultat);
        return resultat.toString();
    }
}
