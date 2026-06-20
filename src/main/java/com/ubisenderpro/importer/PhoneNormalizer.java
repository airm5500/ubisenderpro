package com.ubisenderpro.importer;

/**
 * Normalisation des numéros de téléphone / WhatsApp (section 8.4 de la spec).
 *
 * Règles :
 *  - conserver la valeur d'origine (côté appelant) ;
 *  - produire une valeur normalisée au format international sans espace ;
 *  - contrôler / appliquer le préfixe pays ;
 *  - supprimer les espaces et caractères inutiles ;
 *  - signaler les numéros invalides ;
 *  - ne jamais convertir le téléphone en valeur numérique ;
 *  - conserver les zéros significatifs.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static class Result {
        public final String valeurNormalisee;
        public final boolean valide;
        public final String message;

        Result(String valeurNormalisee, boolean valide, String message) {
            this.valeurNormalisee = valeurNormalisee;
            this.valide = valide;
            this.message = message;
        }
    }

    /**
     * @param brut      le numéro tel que saisi / importé
     * @param prefixePays préfixe pays par défaut sans le « + » (ex : "225")
     */
    public static Result normaliser(String brut, String prefixePays) {
        if (brut == null || brut.trim().isEmpty()) {
            return new Result(null, true, "Numéro absent");
        }

        // Suppression de tout sauf chiffres et signe + de tête.
        String nettoye = brut.trim();
        boolean plus = nettoye.startsWith("+") || nettoye.startsWith("00");
        String chiffres = nettoye.replaceAll("[^0-9]", "");

        if (nettoye.startsWith("00")) {
            chiffres = chiffres.replaceFirst("^00", "");
        }

        if (chiffres.isEmpty()) {
            return new Result(null, false, "Numéro non numérique : " + brut);
        }

        String pref = prefixePays == null ? "" : prefixePays.replaceAll("[^0-9]", "");

        String resultat;
        if (plus || (!pref.isEmpty() && chiffres.startsWith(pref) && chiffres.length() > pref.length() + 6)) {
            // Déjà au format international.
            resultat = chiffres;
        } else {
            // Numéro local : on retire un éventuel 0 de tête puis on préfixe.
            String local = chiffres.replaceFirst("^0+", "");
            resultat = pref + local;
        }

        if (resultat.length() < 8 || resultat.length() > 15) {
            return new Result(resultat, false, "Longueur invalide : " + brut);
        }

        return new Result(resultat, true, null);
    }
}
