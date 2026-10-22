package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChiffrementServiceImplTest {

    private static final String CLE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void unSecretChiffreSeRelitEtNeSeRepetePas() {
        ChiffrementServiceImpl service = new ChiffrementServiceImpl(null, CLE, "inutile");
        String a = service.chiffrer("EAAG-jeton");
        String b = service.chiffrer("EAAG-jeton");
        assertThat(a).startsWith(ChiffrementServiceImpl.PREFIXE).doesNotContain("EAAG").isNotEqualTo(b);
        assertThat(service.dechiffrer(a)).isEqualTo("EAAG-jeton");
        assertThat(service.chiffrer(a)).as("deja chiffre").isEqualTo(a);
        assertThat(service.dechiffrer("ancien-en-clair")).isEqualTo("ancien-en-clair");
        assertThat(service.chiffrer(null)).isNull();
    }

    @Test
    void laCleEstCreeeUneFoisPuisRelueEtUneAutreCleNeLitRien(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("cle-chiffrement.key");
        String chiffre = new ChiffrementServiceImpl(null, "", fichier.toString()).chiffrer("secret");
        assertThat(Files.readString(fichier)).hasSize(44);
        assertThat(new ChiffrementServiceImpl(null, "", fichier.toString()).dechiffrer(chiffre)).isEqualTo("secret");
        assertThat(new ChiffrementServiceImpl(null, CLE, "inutile").dechiffrer(chiffre)).isNull();
    }
}
