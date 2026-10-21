package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import ci.ubipharm.crm.rest.service.CompteWhatsappService;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

class PasserelleWhatsappServiceImplTest {

    private final AtomicReference<String> reponse = new AtomicReference<>();
    private final CompteWhatsappService comptes = mock(CompteWhatsappService.class);
    private HttpServer serveur;
    private PasserelleWhatsappServiceImpl passerelle;

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", echange -> {
            byte[] corps = reponse.get().getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(200, corps.length);
            echange.getResponseBody().write(corps);
            echange.close();
        });
        serveur.start();
        passerelle = new PasserelleWhatsappServiceImpl("http://127.0.0.1:" + serveur.getAddress().getPort(), "", comptes);
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void leCompteIndiqueParWhatsappEstRetenuAvecLeNumeroEcrit() {
        reponse.set("{\"success\":true,\"id\":\"M-1\",\"waNumber\":\"22501020304\"}");
        ResultatEnvoiDTO r = passerelle.envoyerTexte("2250722222222", "2250701020304", "Bonjour");
        assertThat(r.isSucces()).isTrue();
        assertThat(r.getIdentifiant()).isEqualTo("M-1");
        verify(comptes).retenir("2250722222222", "2250701020304", "22501020304");
    }

    @Test
    void unEnvoiRefuseOuUneReponseSansCompteNeRetientRien() {
        reponse.set("{\"success\":false,\"erreur\":\"Numero absent de WhatsApp\"}");
        assertThat(passerelle.envoyerTexte("2250722222222", "2250701020304", "Bonjour").isSucces()).isFalse();
        reponse.set("{\"success\":true,\"id\":\"M-2\"}");
        assertThat(passerelle.envoyerTexte("2250722222222", "2250701020304", "Bonjour").isSucces()).isTrue();
        verify(comptes, never()).retenir(any(), any(), any());
    }
}
