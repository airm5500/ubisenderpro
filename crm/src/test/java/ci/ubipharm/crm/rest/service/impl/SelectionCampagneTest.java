package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelectionCampagneTest {

    @Test
    void leNumeroDeLAgentSAfficheLisible() {
        assertThat(SelectionCampagne.numeroLisible("2250708123456")).isEqualTo("07 08 12 34 56");
        assertThat(SelectionCampagne.numeroLisible("0708123456")).isEqualTo("07 08 12 34 56");
        assertThat(SelectionCampagne.numeroLisible("+33 6 12")).isEqualTo("+33 6 12");
        assertThat(SelectionCampagne.numeroLisible(null)).isEmpty();
    }
}
