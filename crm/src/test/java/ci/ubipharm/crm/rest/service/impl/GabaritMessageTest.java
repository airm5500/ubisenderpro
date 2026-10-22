package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GabaritMessageTest {

    @Test
    void lesVariablesSontRepereesDansLOrdreSansDoublon() {
        assertThat(GabaritMessage.variables("{nom_client}, facture {reference_facture} ; {nom_client} {Pas_une_variable} { solde }"))
                .containsExactly("nom_client", "reference_facture");
    }

    @Test
    void leRenduRemplaceLesVariablesEtNePlanteJamaisSurUnCaractereSpecial() {
        assertThat(GabaritMessage.rendre("Bonjour {nom}, {montant} $ du {inconnue}.", Map.of("nom", "Dr A$1 \\B", "montant", "1 000")))
                .isEqualTo("Bonjour Dr A$1 \\B, 1 000 $ du .");
    }

    @Test
    void lesValeursSontMisesEnFormePourUnMessage() {
        assertThat(SelectionCampagne.formater(new BigDecimal("1500000.00"))).isEqualTo("1 500 000");
        assertThat(SelectionCampagne.formater(new BigDecimal("20000.50"))).isEqualTo("20 000,5");
        assertThat(SelectionCampagne.formater(LocalDate.of(2026, 10, 31))).isEqualTo("31/10/2026");
        assertThat(SelectionCampagne.formater(null)).isEmpty();
    }
}
