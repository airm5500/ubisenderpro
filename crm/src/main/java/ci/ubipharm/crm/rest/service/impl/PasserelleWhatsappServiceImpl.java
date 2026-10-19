package ci.ubipharm.crm.rest.service.impl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasserelleWhatsappServiceImpl implements PasserelleWhatsappService {

    private static final ParameterizedTypeReference<Map<String, Object>> REPONSE = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;

    public PasserelleWhatsappServiceImpl(@Value("${crm.whatsapp.url:http://localhost:3000}") String url,
            @Value("${crm.whatsapp.jeton:}") String jeton) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(url).defaultHeader("X-Api-Token", jeton).requestFactory(fabrique).build();
    }

    @Override
    public EtatSessionDTO demarrer(String numero) {
        return etat(appeler(() -> client.post().uri("/sessions/{id}/start", numero).retrieve().body(REPONSE)));
    }

    @Override
    public EtatSessionDTO etat(String numero) {
        return etat(appeler(() -> client.get().uri("/sessions/{id}/status", numero).retrieve().body(REPONSE)));
    }

    @Override
    public void deconnecter(String numero) {
        appeler(() -> client.post().uri("/sessions/{id}/logout", numero).retrieve().body(REPONSE));
    }

    @Override
    public ResultatEnvoiDTO envoyerTexte(String numero, String destinataire, String texte) {
        return envoyer(numero, "/sessions/{id}/send", Map.of("to", destinataire, "text", texte));
    }

    @Override
    public ResultatEnvoiDTO envoyerFichier(String numero, String destinataire, FichierDTO fichier, String legende) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("to", destinataire);
        corps.put("type", fichier.getTypeContenu().startsWith("image/") ? "image" : "document");
        corps.put("mediaBase64", Base64.getEncoder().encodeToString(fichier.getContenu()));
        corps.put("mimeType", fichier.getTypeContenu());
        corps.put("fileName", fichier.getNom());
        if (legende != null) {
            corps.put("caption", legende);
        }
        return envoyer(numero, "/sessions/{id}/send-media", corps);
    }

    private ResultatEnvoiDTO envoyer(String numero, String chemin, Map<String, Object> corps) {
        try {
            Map<String, Object> reponse = client.post().uri(chemin, numero)
                    .body(corps)
                    .retrieve().body(REPONSE);
            if (reponse != null && Boolean.TRUE.equals(reponse.get("success"))) {
                return ResultatEnvoiDTO.builder().succes(true).identifiant((String) reponse.get("id")).build();
            }
            String erreur = reponse == null ? null : (String) reponse.get("erreur");
            return ResultatEnvoiDTO.builder().erreur(erreur == null ? "Envoi refuse par WhatsApp" : erreur).build();
        } catch (HttpClientErrorException.Conflict e) {
            return ResultatEnvoiDTO.builder().sessionIndisponible(true).erreur("Session WhatsApp de l'agent deconnectee").build();
        } catch (RestClientException e) {
            log.warn("Envoi WhatsApp depuis {} : {}", numero, e.getMessage());
            return ResultatEnvoiDTO.builder().erreurTechnique(true).erreur("Service WhatsApp injoignable").build();
        }
    }

    private Map<String, Object> appeler(java.util.function.Supplier<Map<String, Object>> appel) {
        try {
            return appel.get();
        } catch (RestClientException e) {
            log.warn("Service WhatsApp : {}", e.getMessage());
            throw new ErreurMetierException("Le service WhatsApp ne répond pas ; vérifiez qu'il est démarré.");
        }
    }

    private EtatSessionDTO etat(Map<String, Object> reponse) {
        Map<String, Object> r = reponse == null ? Map.of() : reponse;
        return EtatSessionDTO.builder()
                .statut((String) r.getOrDefault("status", "DECONNECTE"))
                .sante((String) r.getOrDefault("health", "OK"))
                .raison((String) r.get("reason"))
                .qr((String) r.get("qr"))
                .build();
    }
}
