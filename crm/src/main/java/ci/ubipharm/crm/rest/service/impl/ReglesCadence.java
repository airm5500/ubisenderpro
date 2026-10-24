package ci.ubipharm.crm.rest.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;

/**
 * Regles anti-blocage d'un numero WhatsApp, sans base ni reseau (reprises de Prestige, 24/10/2026) : attente entre deux
 * messages avec un plancher, longue pause toutes les N messages, plafonds par jour et par heure, montee en charge d'un
 * numero recent, pause de protection apres des echecs techniques en serie. Les heures sont en UTC (heure d'Abidjan).
 */
final class ReglesCadence {

    /** Attente minimale entre deux messages d'un numero, quels que soient les parametres. */
    static final int PLANCHER_SECONDES = 5;

    private ReglesCadence() {
    }

    /** Reglages lus dans les parametres ; une valeur absurde reprend le defaut. */
    record Reglages(int delaiMin, int delaiMax, int plancher, int pauseApres, int pauseMin, int pauseMax, int plafondJour, int plafondHeure,
            int monteeDepart, int monteePas, int echecsAvantPause, int pauseEchecMinutes) {

        Reglages {
            plancher = Math.max(0, plancher);
            delaiMin = Math.max(plancher, delaiMin < 0 ? 20 : delaiMin);
            delaiMax = Math.max(delaiMin, delaiMax);
            pauseMin = pauseMin <= 0 ? 5 : pauseMin;
            pauseMax = Math.max(pauseMin, pauseMax);
            plafondJour = plafondJour <= 0 ? 200 : plafondJour;
            plafondHeure = plafondHeure <= 0 ? 40 : plafondHeure;
            monteeDepart = monteeDepart <= 0 ? 20 : monteeDepart;
            monteePas = Math.max(0, monteePas);
            pauseEchecMinutes = pauseEchecMinutes <= 0 ? 30 : pauseEchecMinutes;
        }
    }

    /** Ce que le numero a deja fait, lu en base. */
    record Etat(LocalDateTime reprise, String motifReprise, int envoyesAujourdhui, int envoyesDerniereHeure, LocalDateTime plusAncienDerniereHeure,
            LocalDate monteeDepuis) {
    }

    /** Envoi autorise, ou la raison du refus et l'heure a laquelle le numero pourra reprendre. */
    record Verdict(boolean autorise, String motif, LocalDateTime reprise) {

        static final Verdict OK = new Verdict(true, null, null);
    }

    /** Pause, attente, plafond du jour (avec montee en charge) puis plafond par heure, dans cet ordre. */
    static Verdict verdict(Reglages r, Etat e, LocalDateTime maintenant) {
        if (e.reprise() != null && e.reprise().isAfter(maintenant)) {
            return new Verdict(false, e.motifReprise() == null ? "Attente entre deux messages" : e.motifReprise(), e.reprise());
        }
        int plafond = plafondDuJour(r, e.monteeDepuis(), maintenant.toLocalDate());
        if (e.envoyesAujourdhui() >= plafond) {
            String motif = plafond < r.plafondJour() ? "Montée en charge : " + plafond + " messages aujourd'hui" : "Plafond du jour atteint (" + plafond + ")";
            return new Verdict(false, motif, maintenant.toLocalDate().plusDays(1).atStartOfDay());
        }
        if (e.envoyesDerniereHeure() >= r.plafondHeure()) {
            LocalDateTime reprise = e.plusAncienDerniereHeure() == null ? maintenant.plusHours(1) : e.plusAncienDerniereHeure().plusHours(1);
            return new Verdict(false, "Plafond par heure atteint (" + r.plafondHeure() + ")", reprise);
        }
        return Verdict.OK;
    }

    /** Plafond du jour, reduit pour un numero en montee en charge : depart le premier jour, puis + pas par jour. */
    static int plafondDuJour(Reglages r, LocalDate monteeDepuis, LocalDate aujourdhui) {
        if (monteeDepuis == null) {
            return r.plafondJour();
        }
        long jours = Math.max(0, ChronoUnit.DAYS.between(monteeDepuis, aujourdhui));
        long montee = r.monteeDepart() + r.monteePas() * jours;
        return (int) Math.min(r.plafondJour(), montee);
    }

    /** Attente apres un message reussi : la longue pause quand la serie atteint sa longueur, sinon l'attente ordinaire. */
    static Duration attente(Reglages r, int serie, RandomGenerator alea) {
        if (r.pauseApres() > 0 && serie >= r.pauseApres()) {
            return Duration.ofSeconds(entre(r.pauseMin() * 60L, r.pauseMax() * 60L, alea));
        }
        return Duration.ofSeconds(entre(r.delaiMin(), r.delaiMax(), alea));
    }

    /** Une serie complete declenche la longue pause et repart de zero. */
    static boolean pauseLongue(Reglages r, int serie) {
        return r.pauseApres() > 0 && serie >= r.pauseApres();
    }

    /** Le numero a echoue assez de fois de suite pour etre mis au repos. */
    static boolean pauseProtection(Reglages r, int echecsConsecutifs) {
        return r.echecsAvantPause() > 0 && echecsConsecutifs >= r.echecsAvantPause();
    }

    private static long entre(long min, long max, RandomGenerator alea) {
        return min >= max ? min : alea.nextLong(min, max + 1);
    }
}
