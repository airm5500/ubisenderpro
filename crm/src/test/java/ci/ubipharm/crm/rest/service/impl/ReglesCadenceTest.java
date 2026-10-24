package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Etat;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Reglages;
import ci.ubipharm.crm.rest.service.impl.ReglesCadence.Verdict;

/** Regles anti-blocage d'un numero WhatsApp, avec une heure et un tirage fixes. */
class ReglesCadenceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 3, 10, 0);
    /** Valeurs livrees : 20 a 45 s, pause de 5 a 12 min tous les 15 messages, 200 par jour, 40 par heure, montee 20 + 20 par jour. */
    private static final Reglages DEFAUT = new Reglages(20, 45, 5, 15, 5, 12, 200, 40, 20, 20, 3, 30);

    private static Etat etat(int jour, int heure) {
        return new Etat(null, null, jour, heure, heure == 0 ? null : MAINTENANT.minusMinutes(50), null);
    }

    @Test
    void lAttenteNEstJamaisFixeEtResteDansSesBornes() {
        Random alea = new Random(7);
        Set<Long> tirages = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            long s = ReglesCadence.attente(DEFAUT, 3, alea).toSeconds();
            assertThat(s).isBetween(20L, 45L);
            tirages.add(s);
        }
        assertThat(tirages).hasSizeGreaterThan(10);
    }

    @Test
    void leDelaiNeDescendJamaisSousLePlancherEtUneValeurAbsurdeReprendLeDefaut() {
        Reglages zero = new Reglages(0, 0, 5, 15, 5, 12, 200, 40, 20, 20, 3, 30);
        assertThat(zero.delaiMin()).isEqualTo(5);
        assertThat(ReglesCadence.attente(zero, 1, new Random(1))).isEqualTo(Duration.ofSeconds(5));
        Reglages negatif = new Reglages(-3, 10, 5, 15, 5, 12, -1, 0, 0, -2, 3, 0);
        assertThat(negatif.delaiMin()).isEqualTo(20);
        assertThat(negatif.delaiMax()).isEqualTo(20);
        assertThat(negatif.plafondJour()).isEqualTo(200);
        assertThat(negatif.plafondHeure()).isEqualTo(40);
        assertThat(negatif.monteeDepart()).isEqualTo(20);
        assertThat(negatif.monteePas()).isZero();
        assertThat(negatif.pauseEchecMinutes()).isEqualTo(30);
        Reglages inverse = new Reglages(60, 30, 5, 15, 9, 4, 200, 40, 20, 20, 3, 30);
        assertThat(inverse.delaiMax()).isEqualTo(60);
        assertThat(inverse.pauseMax()).isEqualTo(9);
    }

    @Test
    void laLonguePauseTombeAuQuinziemeMessage() {
        Random alea = new Random(3);
        assertThat(ReglesCadence.pauseLongue(DEFAUT, 14)).isFalse();
        assertThat(ReglesCadence.attente(DEFAUT, 14, alea).toSeconds()).isBetween(20L, 45L);
        assertThat(ReglesCadence.pauseLongue(DEFAUT, 15)).isTrue();
        for (int i = 0; i < 50; i++) {
            assertThat(ReglesCadence.attente(DEFAUT, 15, alea).toMinutes()).isBetween(5L, 12L);
        }
        Reglages sansPause = new Reglages(20, 45, 5, 0, 5, 12, 200, 40, 20, 20, 3, 30);
        assertThat(ReglesCadence.pauseLongue(sansPause, 1000)).isFalse();
    }

    @Test
    void lePlafondParHeureFaitAttendreLaSortieDuPlusAncienMessageDeLHeure() {
        assertThat(ReglesCadence.verdict(DEFAUT, etat(39, 39), MAINTENANT).autorise()).isTrue();
        Verdict v = ReglesCadence.verdict(DEFAUT, etat(40, 40), MAINTENANT);
        assertThat(v.autorise()).isFalse();
        assertThat(v.motif()).isEqualTo("Plafond par heure atteint (40)");
        assertThat(v.reprise()).isEqualTo(MAINTENANT.plusMinutes(10));
    }

    @Test
    void lePlafondDuJourRenvoieAuLendemain() {
        Verdict v = ReglesCadence.verdict(DEFAUT, etat(200, 0), MAINTENANT);
        assertThat(v.autorise()).isFalse();
        assertThat(v.motif()).isEqualTo("Plafond du jour atteint (200)");
        assertThat(v.reprise()).isEqualTo(LocalDateTime.of(2026, 11, 4, 0, 0));
    }

    @Test
    void laMonteeEnChargeAugmenteLePlafondChaqueJour() {
        LocalDate debut = MAINTENANT.toLocalDate();
        assertThat(ReglesCadence.plafondDuJour(DEFAUT, null, debut)).as("numero ancien").isEqualTo(200);
        assertThat(ReglesCadence.plafondDuJour(DEFAUT, debut, debut)).as("jour 0").isEqualTo(20);
        assertThat(ReglesCadence.plafondDuJour(DEFAUT, debut, debut.plusDays(1))).as("jour 1").isEqualTo(40);
        assertThat(ReglesCadence.plafondDuJour(DEFAUT, debut, debut.plusDays(10))).as("jour 10, plafond atteint").isEqualTo(200);
        assertThat(ReglesCadence.plafondDuJour(DEFAUT, debut.plusDays(2), debut)).as("date future").isEqualTo(20);

        Verdict v = ReglesCadence.verdict(DEFAUT, new Etat(null, null, 20, 5, MAINTENANT.minusMinutes(5), debut), MAINTENANT);
        assertThat(v.autorise()).isFalse();
        assertThat(v.motif()).isEqualTo("Montée en charge : 20 messages aujourd'hui");
    }

    @Test
    void uneRepriseFutureBloqueAvecSonMotifPuisLibereLeNumero() {
        Etat enPause = new Etat(MAINTENANT.plusMinutes(7), "Pause longue après 15 messages", 15, 15, MAINTENANT.minusMinutes(30), null);
        Verdict v = ReglesCadence.verdict(DEFAUT, enPause, MAINTENANT);
        assertThat(v.autorise()).isFalse();
        assertThat(v.motif()).isEqualTo("Pause longue après 15 messages");
        assertThat(v.reprise()).isEqualTo(MAINTENANT.plusMinutes(7));
        assertThat(ReglesCadence.verdict(DEFAUT, enPause, MAINTENANT.plusMinutes(8)).autorise()).isTrue();

        Etat attente = new Etat(MAINTENANT.plusSeconds(20), null, 1, 1, MAINTENANT, null);
        assertThat(ReglesCadence.verdict(DEFAUT, attente, MAINTENANT).motif()).isEqualTo("Attente entre deux messages");
    }

    @Test
    void laPauseDeProtectionArriveAuTroisiemeEchecTechnique() {
        assertThat(ReglesCadence.pauseProtection(DEFAUT, 2)).isFalse();
        assertThat(ReglesCadence.pauseProtection(DEFAUT, 3)).isTrue();
        Reglages jamais = new Reglages(20, 45, 5, 15, 5, 12, 200, 40, 20, 20, 0, 30);
        assertThat(ReglesCadence.pauseProtection(jamais, 99)).isFalse();
    }

    @Test
    void lesMotsDArretSontReconnusAvecPonctuationEtMajuscules() {
        assertThat(DesabonnementServiceImpl.normaliser("Stop !")).isEqualTo("STOP");
        assertThat(DesabonnementServiceImpl.normaliser("désinscrire.")).isEqualTo("DESINSCRIRE");
        assertThat(DesabonnementServiceImpl.normaliser("  unsubscribe ")).isEqualTo("UNSUBSCRIBE");
        assertThat(DesabonnementServiceImpl.MOTS_PAR_DEFAUT).contains("DESINSCRIRE", "UNSUBSCRIBE");
    }
}
