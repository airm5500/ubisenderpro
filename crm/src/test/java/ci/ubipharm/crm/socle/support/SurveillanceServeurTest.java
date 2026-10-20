package ci.ubipharm.crm.socle.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;

class SurveillanceServeurTest {

    private final SupportDiagnosticService diagnostic = mock(SupportDiagnosticService.class);

    private SurveillanceServeur surveillance(Path temoin, String journal) {
        return new SurveillanceServeur(diagnostic, mock(ParametreService.class), mock(PasserelleWhatsappService.class), mock(DataSource.class),
                mock(JdbcTemplate.class), temoin.toString(), journal);
    }

    @Test
    void uneAlerteDemandeTroisRelevesPuisSeTaitUneHeure(@TempDir Path dossier) {
        SurveillanceServeur s = surveillance(dossier.resolve("t.properties"), "");
        assertThat(s.alerte("MEMOIRE", true, 3)).isFalse();
        assertThat(s.alerte("MEMOIRE", true, 3)).isFalse();
        assertThat(s.alerte("MEMOIRE", true, 3)).isTrue();
        // Silence d'une heure, meme si la condition persiste
        for (int i = 0; i < 5; i++) {
            assertThat(s.alerte("MEMOIRE", true, 3)).isFalse();
        }
        // Un releve normal remet le compte a zero ; les codes sont independants
        assertThat(s.alerte("POOL", true, 3)).isFalse();
        assertThat(s.alerte("POOL", false, 3)).isFalse();
        assertThat(s.alerte("POOL", true, 3)).isFalse();
        assertThat(s.alerte("DISQUE", true, 1)).isTrue();
    }

    @Test
    void unArretNormalNeSignaleRien(@TempDir Path dossier) {
        Path temoin = dossier.resolve("t.properties");
        SurveillanceServeur s = surveillance(temoin, "");
        s.auDemarrage();
        assertThat(temoin).exists();
        s.arretNormal();
        assertThat(temoin).doesNotExist();
        surveillance(temoin, "").auDemarrage();
        verify(diagnostic, never()).signaler(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unTemoinRestantSignaleUnArretBrutalAvecLaFinDuJournal(@TempDir Path dossier) throws Exception {
        Path temoin = dossier.resolve("t.properties");
        Instant vie = Instant.now().minus(42, ChronoUnit.MINUTES);
        Files.writeString(temoin, "vie=" + vie + "\ndemarre=" + vie.minus(3, ChronoUnit.DAYS) + "\nmemoire=512 Mo utilisés sur 1024 Mo\n");
        Path journal = dossier.resolve("crm.log");
        Files.writeString(journal, "ligne ancienne\nERROR derniere erreur avant coupure\n2026-10-20 INFO Starting CrmApplication using Java 25\nnouveau\n");
        surveillance(temoin, journal.toString()).auDemarrage();
        verify(diagnostic).signaler(eq("SURVEILLANCE"), eq("Arrêt brutal du serveur"), eq("Le serveur s'est arrêté sans arrêt normal"),
                argThat(d -> d.contains("Indisponibilité : environ 42 min") && d.contains("512 Mo utilisés")
                        && d.contains("ERROR derniere erreur avant coupure") && !d.contains("Starting CrmApplication")
                        && d.contains("coupure de courant")),
                isNull(), isNull());
        // Le temoin est reecrit pour la nouvelle marche
        assertThat(Files.readString(temoin)).doesNotContain(vie.toString());
    }
}
