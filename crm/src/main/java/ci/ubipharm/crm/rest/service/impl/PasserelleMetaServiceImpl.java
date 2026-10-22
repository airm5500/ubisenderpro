package ci.ubipharm.crm.rest.service.impl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ci.ubipharm.crm.rest.service.ChiffrementService;
import ci.ubipharm.crm.rest.service.PasserelleMetaService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class PasserelleMetaServiceImpl implements PasserelleMetaService {

    private static final ParameterizedTypeReference<Map<String, Object>> REPONSE = new ParameterizedTypeReference<>() {
    };
    /** Codes Meta d'un jeton expire ou revoque. */
    private static final List<Integer> JETON_REFUSE = List.of(190, 102, 10);

    private final RestClient client;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final ChiffrementService chiffrementService;

    private record Compte(String jeton, String version) {
    }

    public PasserelleMetaServiceImpl(@Value("${crm.meta.url:https://graph.facebook.com}") String url, NamedParameterJdbcTemplate jdbc,
                                     JsonMapper jsonMapper, ChiffrementService chiffrementService) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(url).requestFactory(fabrique).build();
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.chiffrementService = chiffrementService;
    }

    @Override
    public String verifierNumero(String phoneNumberId) {
        Compte compte = compte();
        if (compte.jeton() == null) {
            throw new ErreurMetierException("Renseignez d'abord le jeton d'accès du compte Meta.");
        }
        try {
            Map<String, Object> r = client.get().uri("/{v}/{id}?fields=display_phone_number,verified_name", compte.version(), phoneNumberId)
                    .header("Authorization", "Bearer " + compte.jeton()).retrieve().body(REPONSE);
            r = r == null ? Map.of() : r;
            return r.getOrDefault("display_phone_number", phoneNumberId) + (r.get("verified_name") != null ? " · " + r.get("verified_name") : "");
        } catch (HttpStatusCodeException e) {
            throw new ErreurMetierException("Meta refuse ce numéro : " + messageErreur(e) + ".");
        } catch (RestClientException e) {
            log.warn("API Meta : {}", e.getMessage());
            throw new ErreurMetierException("L'API de Meta ne répond pas ; vérifiez l'accès Internet du serveur.");
        }
    }

    @Override
    public ResultatEnvoiDTO envoyerTexte(String phoneNumberId, String destinataire, String texte) {
        return envoyer(phoneNumberId, corps(destinataire, "text", Map.of("body", texte, "preview_url", false)));
    }

    @Override
    public ResultatEnvoiDTO envoyerFichier(String phoneNumberId, String destinataire, FichierDTO fichier, String legende) {
        ResultatEnvoiDTO depot = new ResultatEnvoiDTO();
        String media = deposer(phoneNumberId, fichier, depot);
        if (media == null) {
            return depot;
        }
        String type = image(fichier) ? "image" : "document";
        Map<String, Object> contenu = new HashMap<>();
        contenu.put("id", media);
        if (!image(fichier)) {
            contenu.put("filename", fichier.getNom());
        }
        if (legende != null) {
            contenu.put("caption", legende);
        }
        return envoyer(phoneNumberId, corps(destinataire, type, contenu));
    }

    @Override
    public ResultatEnvoiDTO envoyerModele(String phoneNumberId, String destinataire, String modele, String langue, List<String> parametres,
                                          FichierDTO entete) {
        List<Map<String, Object>> composants = new ArrayList<>();
        if (entete != null) {
            ResultatEnvoiDTO depot = new ResultatEnvoiDTO();
            String media = deposer(phoneNumberId, entete, depot);
            if (media == null) {
                return depot;
            }
            String type = image(entete) ? "image" : "document";
            Map<String, Object> fichier = new HashMap<>(Map.of("id", media));
            if (!image(entete)) {
                fichier.put("filename", entete.getNom());
            }
            composants.add(Map.of("type", "header", "parameters", List.of(Map.of("type", type, type, fichier))));
        }
        if (!parametres.isEmpty()) {
            composants.add(Map.of("type", "body", "parameters", parametres.stream().map(p -> Map.of("type", "text", "text", p)).toList()));
        }
        Map<String, Object> gabarit = new HashMap<>();
        gabarit.put("name", modele);
        gabarit.put("language", Map.of("code", langue));
        if (!composants.isEmpty()) {
            gabarit.put("components", composants);
        }
        return envoyer(phoneNumberId, corps(destinataire, "template", gabarit));
    }

    private static Map<String, Object> corps(String destinataire, String type, Object contenu) {
        return Map.of("messaging_product", "whatsapp", "recipient_type", "individual", "to", destinataire, "type", type, type, contenu);
    }

    private ResultatEnvoiDTO envoyer(String phoneNumberId, Map<String, Object> corps) {
        Compte compte = compte();
        if (compte.jeton() == null) {
            return ResultatEnvoiDTO.builder().sessionIndisponible(true).erreur("Jeton d'accès Meta non renseigné").build();
        }
        try {
            Map<String, Object> reponse = client.post().uri("/{v}/{id}/messages", compte.version(), phoneNumberId)
                    .header("Authorization", "Bearer " + compte.jeton()).contentType(MediaType.APPLICATION_JSON)
                    .body(corps).retrieve().body(REPONSE);
            Object messages = reponse == null ? null : reponse.get("messages");
            if (messages instanceof List<?> liste && !liste.isEmpty() && liste.getFirst() instanceof Map<?, ?> m && m.get("id") != null) {
                return ResultatEnvoiDTO.builder().succes(true).identifiant(m.get("id").toString()).build();
            }
            return ResultatEnvoiDTO.builder().erreur("Réponse de Meta sans identifiant de message").build();
        } catch (HttpStatusCodeException e) {
            return echec(e);
        } catch (RestClientException e) {
            log.warn("Envoi Meta depuis {} : {}", phoneNumberId, e.getMessage());
            return ResultatEnvoiDTO.builder().erreurTechnique(true).erreur("API de Meta injoignable").build();
        }
    }

    /** Depose le fichier chez Meta et renvoie son identifiant ; en cas d'echec, le resultat decrit l'erreur. */
    private String deposer(String phoneNumberId, FichierDTO fichier, ResultatEnvoiDTO resultat) {
        Compte compte = compte();
        if (compte.jeton() == null) {
            resultat.setSessionIndisponible(true);
            resultat.setErreur("Jeton d'accès Meta non renseigné");
            return null;
        }
        MultipartBodyBuilder parties = new MultipartBodyBuilder();
        parties.part("messaging_product", "whatsapp");
        parties.part("type", fichier.getTypeContenu());
        parties.part("file", new ByteArrayResource(fichier.getContenu()) {
            @Override
            public String getFilename() {
                return fichier.getNom();
            }
        }).contentType(MediaType.parseMediaType(fichier.getTypeContenu()));
        try {
            Map<String, Object> reponse = client.post().uri("/{v}/{id}/media", compte.version(), phoneNumberId)
                    .header("Authorization", "Bearer " + compte.jeton()).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parties.build()).retrieve().body(REPONSE);
            if (reponse != null && reponse.get("id") != null) {
                return reponse.get("id").toString();
            }
            resultat.setErreur(fichier.getNom() + " refusé par Meta");
        } catch (HttpStatusCodeException e) {
            ResultatEnvoiDTO echec = echec(e);
            resultat.setErreur(fichier.getNom() + " : " + echec.getErreur());
            resultat.setSessionIndisponible(echec.isSessionIndisponible());
            resultat.setErreurTechnique(echec.isErreurTechnique());
        } catch (RestClientException e) {
            log.warn("Depot Meta depuis {} : {}", phoneNumberId, e.getMessage());
            resultat.setErreurTechnique(true);
            resultat.setErreur("API de Meta injoignable");
        }
        return null;
    }

    /** Jeton refuse : le message attend ; erreur serveur : nouvel essai ; sinon echec avec le motif donne par Meta. */
    private ResultatEnvoiDTO echec(HttpStatusCodeException e) {
        String message = messageErreur(e);
        if (e.getStatusCode().value() == 401 || JETON_REFUSE.contains(codeErreur(e))) {
            return ResultatEnvoiDTO.builder().sessionIndisponible(true).erreur("Jeton Meta refusé : " + message).build();
        }
        if (e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429) {
            return ResultatEnvoiDTO.builder().erreurTechnique(true).erreur("Meta indisponible : " + message).build();
        }
        return ResultatEnvoiDTO.builder().erreur(message).build();
    }

    private JsonNode erreur(HttpStatusCodeException e) {
        try {
            return jsonMapper.readTree(e.getResponseBodyAsString()).path("error");
        } catch (RuntimeException illisible) {
            return jsonMapper.missingNode();
        }
    }

    private int codeErreur(HttpStatusCodeException e) {
        return erreur(e).path("code").asInt(0);
    }

    private String messageErreur(HttpStatusCodeException e) {
        JsonNode erreur = erreur(e);
        String details = erreur.path("error_data").path("details").asString("");
        String message = erreur.path("message").asString("");
        if (!details.isBlank()) {
            return details;
        }
        return message.isBlank() ? "HTTP " + e.getStatusCode().value() : message;
    }

    private static boolean image(FichierDTO fichier) {
        return fichier.getTypeContenu() != null && fichier.getTypeContenu().startsWith("image/");
    }

    private Compte compte() {
        return jdbc.query("select jeton_acces, version_api from crm_compte_meta where id = 1", Map.of(),
                (rs, i) -> new Compte(rs.getString(1) == null || rs.getString(1).isBlank() ? null : chiffrementService.dechiffrer(rs.getString(1)), rs.getString(2)))
                .stream().findFirst().orElse(new Compte(null, "v21.0"));
    }
}
