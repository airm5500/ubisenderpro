package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportDiagnosticServiceImplTest {

    @Test
    void laSignatureIgnoreLesChiffresEtLesEspaces() {
        assertThat(SupportDiagnosticServiceImpl.normaliser("Client 1234  introuvable\n(ligne 7)")).isEqualTo("Client introuvable (ligne )");
        assertThat(SupportDiagnosticServiceImpl.signature("TypeError", "Facture 12 absente"))
                .isEqualTo(SupportDiagnosticServiceImpl.signature("TypeError", "Facture 98765 absente"))
                .isNotEqualTo(SupportDiagnosticServiceImpl.signature("RangeError", "Facture 12 absente"))
                .hasSize(64);
        assertThat(SupportDiagnosticServiceImpl.normaliser(null)).isEmpty();
    }
}
