package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SupportExperienceServiceImplTest {

    @Test
    void lAppelEstNormaliseSansParametresNiIdentifiants() {
        assertThat(SupportExperienceServiceImpl.cleAppel("get /api/factures/1234?page=2#x")).isEqualTo("GET /api/factures/{id}");
        assertThat(SupportExperienceServiceImpl.cleAppel("POST /api/campagnes/12/lancement")).isEqualTo("POST /api/campagnes/{id}/lancement");
        assertThat(SupportExperienceServiceImpl.cleAppel("GET /api/fichiers/3f2a9c1e-0b4d-4e8f-9a7b-1c2d3e4f5a6b"))
                .isEqualTo("GET /api/fichiers/{id}");
        assertThat(SupportExperienceServiceImpl.cleAppel("GET /api/clients/CLI2026A/factures")).isEqualTo("GET /api/clients/{id}/factures");
        assertThat(SupportExperienceServiceImpl.cleAppel("GET /api/parametres/sms2")).isEqualTo("GET /api/parametres/sms2");
        assertThat(SupportExperienceServiceImpl.cleAppel("GET /api/support/rum")).isNull();
        assertThat(SupportExperienceServiceImpl.cleAppel("GET https://ailleurs.ci/api/x")).isNull();
        assertThat(SupportExperienceServiceImpl.cleAppel("TRACE /api/x")).isNull();
        assertThat(SupportExperienceServiceImpl.cleAppel("GET /api/" + "a".repeat(300))).hasSize(160);
    }

    @Test
    void laTrancheEstLaPeriodeDeCinqMinutes() {
        assertThat(SupportExperienceServiceImpl.tranche(LocalDateTime.of(2026, 10, 20, 9, 37, 59))).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 35));
        assertThat(SupportExperienceServiceImpl.tranche(LocalDateTime.of(2026, 10, 20, 9, 40, 0))).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 40));
    }
}
