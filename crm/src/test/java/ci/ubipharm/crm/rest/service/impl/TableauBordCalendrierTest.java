package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import ci.ubipharm.crm.socle.erreur.ErreurMetierException;

/** Regles du calendrier d'activite des tableaux de bord : jours des evenements, periode admise, libelles. */
class TableauBordCalendrierTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 28);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 8);

    @Test
    void unEvenementPonctuelNeCompteQueSonJour() {
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2026, 10, 15), null, false, DEBUT, FIN))
                .containsExactly(LocalDate.of(2026, 10, 15));
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2025, 10, 15), null, false, DEBUT, FIN)).isEmpty();
    }

    @Test
    void unEvenementSurPlusieursJoursEstCoupeAuxBornesDeLaPeriode() {
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 29), false, DEBUT, FIN))
                .containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29));
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 12), false, DEBUT, FIN))
                .containsExactly(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8));
    }

    @Test
    void unEvenementAnnuelRevientChaqueAnnee() {
        // Saisi pour 2027 (jour ferie livre) : il apparait aussi en 2026
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2027, 11, 1), null, true, DEBUT, FIN))
                .containsExactly(LocalDate.of(2026, 11, 1));
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2020, 10, 3), LocalDate.of(2020, 10, 4), true, DEBUT, FIN))
                .containsExactly(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4));
    }

    @Test
    void unEvenementAnnuelACachevalSurLAnneeEstRetrouve() {
        LocalDate debut = LocalDate.of(2026, 12, 28);
        LocalDate fin = LocalDate.of(2027, 1, 10);
        assertThat(TableauBordServiceImpl.joursEvenement(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 2), true, debut, fin))
                .containsExactly(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2));
    }

    @Test
    void laPeriodeEstLimiteeA62Jours() {
        TableauBordServiceImpl.verifierPeriodeCalendrier(DEBUT, DEBUT.plusDays(62));
        assertThatThrownBy(() -> TableauBordServiceImpl.verifierPeriodeCalendrier(DEBUT, DEBUT.plusDays(63)))
                .isInstanceOf(ErreurMetierException.class).hasMessage("Le calendrier couvre au plus 62 jours.");
        assertThatThrownBy(() -> TableauBordServiceImpl.verifierPeriodeCalendrier(FIN, DEBUT))
                .isInstanceOf(ErreurMetierException.class).hasMessage("Le début de la période doit précéder sa fin.");
    }

    @Test
    void lesLibellesSAccordent() {
        assertThat(TableauBordServiceImpl.pluriel(1, "relance envoyée", "relances envoyées")).isEqualTo("1 relance envoyée");
        assertThat(TableauBordServiceImpl.pluriel(4, "relance envoyée", "relances envoyées")).isEqualTo("4 relances envoyées");
    }
}
