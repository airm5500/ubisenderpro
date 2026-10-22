package ci.ubipharm.crm.rest.service.impl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ci.ubipharm.crm.rest.service.dto.ChampImportDTO;

/**
 * Conversion des valeurs texte lues dans les fichiers d'import :
 * en-tetes, dates, montants et numeros WhatsApp, avec les formats courants en Cote d'Ivoire.
 */
public final class ValeursImport {

    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.STRICT));

    private ValeursImport() {
    }

    /** "Date d'échéance" -> "date_d_echeance" */
    public static String normaliserEntete(String entete) {
        if (entete == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(entete, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Texte de comparaison insensible a la casse, aux accents et a la ponctuation. */
    public static String normaliserLibelle(String libelle) {
        return normaliserEntete(libelle);
    }

    /** Premiere valeur non vide parmi les en-tetes acceptes pour un champ. */
    public static String valeur(Map<String, String> valeurs, List<String> alias) {
        for (String a : alias) {
            String v = valeurs.get(a);
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }

    /** Le fichier contient-il au moins un des en-tetes acceptes ? */
    public static boolean colonnePresente(List<String> entetes, List<String> alias) {
        return alias.stream().anyMatch(entetes::contains);
    }

    public static Optional<LocalDate> date(String texte) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        String t = texte.strip();
        // Date Excel restee au format numerique (jours depuis le 30/12/1899)
        if (t.matches("\\d{5}(\\.0+)?")) {
            return Optional.of(LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(t.replaceAll("\\..*", ""))));
        }
        if (t.length() > 10 && t.charAt(10) == 'T') {
            t = t.substring(0, 10);
        }
        for (DateTimeFormatter format : FORMATS_DATE) {
            try {
                return Optional.of(LocalDate.parse(t, format));
            } catch (DateTimeParseException e) {
                // format suivant
            }
        }
        return Optional.empty();
    }

    /** "1 234 567,50", "1234567.5", "1.234.567,50", "150 000 FCFA" */
    public static Optional<BigDecimal> montant(String texte) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        String t = texte.replaceAll("(?i)f\\s*cfa|xof|cfa", "").replaceAll("[\\s\\u00A0\\u202F]", "");
        if (t.isEmpty()) {
            return Optional.empty();
        }
        int virgule = t.lastIndexOf(',');
        int point = t.lastIndexOf('.');
        if (virgule >= 0 && point >= 0) {
            // Le dernier separateur rencontre est le separateur decimal
            t = virgule > point ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else if (virgule >= 0) {
            t = t.indexOf(',') == virgule && t.length() - virgule - 1 != 3 ? t.replace(',', '.') : t.replace(",", "");
        } else if (point >= 0 && t.indexOf('.') != point) {
            t = t.replace(".", "");
        }
        try {
            return Optional.of(new BigDecimal(t));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Numero WhatsApp au format international sans "+" : 225 suivi de 10 chiffres.
     * Accepte "07 00 00 00 01", "+225 0700000001", "00225...".
     */
    public static Optional<String> numeroWhatsapp(String texte, String prefixePays) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        String chiffres = texte.replaceAll("[^0-9]", "");
        if (chiffres.startsWith("00")) {
            chiffres = chiffres.substring(2);
        }
        if (chiffres.length() == 10) {
            chiffres = prefixePays + chiffres;
        }
        return chiffres.matches(prefixePays + "\\d{10}") ? Optional.of(chiffres) : Optional.empty();
    }

    /**
     * Definition d'un champ importable : son code est le premier alias, et son libelle normalise
     * est aussi reconnu, pour qu'un exemplaire ou un fichier de rejets se reimporte tel quel.
     */
    public static ChampImportDTO champ(String libelle, boolean obligatoire, String exemple, List<String> alias) {
        List<String> tous = new java.util.ArrayList<>(alias);
        String libelleNormalise = normaliserEntete(libelle);
        if (!tous.contains(libelleNormalise)) {
            tous.add(libelleNormalise);
        }
        return ChampImportDTO.builder()
                .code(alias.getFirst())
                .libelle(libelle)
                .obligatoire(obligatoire)
                .exemple(exemple)
                .alias(List.copyOf(tous))
                .build();
    }

    /** Donnees d'une ligne apres correction : seuls les champs connus sont pris, une valeur vide efface le champ. */
    public static Map<String, String> corriger(List<ChampImportDTO> champs, Map<String, String> donnees, Map<String, String> corrections) {
        Map<String, String> resultat = new java.util.LinkedHashMap<>(donnees);
        java.util.Set<String> codes = champs.stream().map(ChampImportDTO::getCode).collect(java.util.stream.Collectors.toSet());
        corrections.forEach((champ, valeur) -> {
            if (!codes.contains(champ)) {
                return;
            }
            if (valeur == null || valeur.isBlank()) {
                resultat.remove(champ);
            } else {
                resultat.put(champ, valeur.strip());
            }
        });
        return resultat;
    }

    public static String montantLisible(BigDecimal montant) {
        if (montant == null) {
            return "vide";
        }
        return String.format(Locale.FRANCE, "%,.2f", montant).replace(' ', ' ').replace(' ', ' ');
    }
}
