package ci.ubipharm.crm.rest.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import ci.ubipharm.crm.socle.erreur.ErreurMetierException;

/**
 * Repetition d'une campagne validee une fois : chaque jour, chaque semaine, chaque mois ou chaque annee jusqu'a une date de fin.
 * Les envois se calculent depuis le premier, pour qu'un mois court ne decale pas les suivants.
 */
final class Recurrence {

    static final Set<String> FREQUENCES = Set.of("QUOTIDIENNE", "HEBDOMADAIRE", "MENSUELLE", "ANNUELLE");

    private Recurrence() {
    }

    /** Refuse une repetition incoherente ; sans frequence, la campagne part une seule fois. */
    static void verifier(String frequence, LocalDate fin, LocalDateTime dateExecution, LocalDate aujourdhui) {
        if (frequence == null) {
            if (fin != null) {
                throw new ErreurMetierException("Choisissez la fréquence de répétition ou retirez la date de fin.");
            }
            return;
        }
        if (!FREQUENCES.contains(frequence)) {
            throw new ErreurMetierException("Fréquence de répétition inconnue : " + frequence + ".");
        }
        if (fin == null) {
            throw new ErreurMetierException("Indiquez jusqu'à quelle date la campagne se répète.");
        }
        LocalDate premier = dateExecution == null ? aujourdhui : dateExecution.toLocalDate();
        if (fin.isBefore(premier)) {
            throw new ErreurMetierException("La fin de la répétition précède le premier envoi.");
        }
    }

    /**
     * Premier envoi prevu apres l'instant donne, ou null si la repetition est finie. Un envoi manque (serveur arrete)
     * n'est pas rattrape : la campagne reprend au suivant.
     */
    static LocalDateTime prochaine(LocalDateTime debut, String frequence, LocalDate fin, LocalDateTime apres) {
        for (long rang = 1; ; rang++) {
            LocalDateTime envoi = switch (frequence) {
                case "QUOTIDIENNE" -> debut.plusDays(rang);
                case "HEBDOMADAIRE" -> debut.plusWeeks(rang);
                case "MENSUELLE" -> debut.plusMonths(rang);
                case "ANNUELLE" -> debut.plusYears(rang);
                default -> throw new IllegalArgumentException(frequence);
            };
            if (envoi.toLocalDate().isAfter(fin)) {
                return null;
            }
            if (envoi.isAfter(apres)) {
                return envoi;
            }
        }
    }
}
