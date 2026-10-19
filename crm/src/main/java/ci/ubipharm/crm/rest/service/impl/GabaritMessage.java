package ci.ubipharm.crm.rest.service.impl;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;
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

    /**
     * Valeurs des variables citees, dans leur ordre d'apparition et une par ligne : les parametres {{1}}, {{2}}... d'un modele
     * approuve par Meta. Meta refuse un parametre vide, multiligne ou avec plus de quatre espaces de suite.
     */
    static String parametres(String contenu, Map<String, String> valeurs) {
        return variables(contenu).stream().map(code -> {
            String valeur = valeurs.getOrDefault(code, "");
            String nette = valeur == null ? "" : valeur.replaceAll("\\s*\\R\\s*", " · ").replaceAll("\\s+", " ").strip();
            return nette.isEmpty() ? "-" : nette;
        }).collect(Collectors.joining("\n"));
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
