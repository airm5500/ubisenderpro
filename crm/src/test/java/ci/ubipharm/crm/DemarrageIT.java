package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Demarrage avec le vrai serveur web (Tomcat), comme en production : les filtres servlet (FiltreLenteurs) se creent avant la couche
 * JPA, ce que les tests MockMvc ne verifient pas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class DemarrageIT {

    @LocalServerPort
    int port;

    @Test
    void leServeurWebDemarreEtRepond() throws Exception {
        HttpResponse<String> reponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(reponse.statusCode()).isEqualTo(200);
    }
}
