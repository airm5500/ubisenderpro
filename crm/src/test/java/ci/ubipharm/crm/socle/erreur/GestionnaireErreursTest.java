package ci.ubipharm.crm.socle.erreur;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import ci.ubipharm.crm.socle.dto.ErreurDTO;

class GestionnaireErreursTest {

    private final GestionnaireErreurs gestionnaire = new GestionnaireErreurs();

    @Test
    void uneErreurInattendueNeRevelePasSonDetail() {
        ResponseEntity<ErreurDTO> reponse = gestionnaire.erreurInattendue(
                new IllegalStateException("Table crm_client introuvable sur jdbc:mariadb://serveur"));

        assertThat(reponse.getStatusCode().value()).isEqualTo(500);
        assertThat(reponse.getBody().getReference()).hasSize(8);
        assertThat(reponse.getBody().getMessage())
                .contains(reponse.getBody().getReference())
                .doesNotContain("crm_client")
                .doesNotContain("jdbc");
    }

    @Test
    void uneErreurMetierGardeSonMessage() {
        ResponseEntity<ErreurDTO> reponse = gestionnaire.erreurMetier(new ErreurMetierException("Le client est archive."));

        assertThat(reponse.getStatusCode().value()).isEqualTo(400);
        assertThat(reponse.getBody().getMessage()).isEqualTo("Le client est archive.");
    }
}
