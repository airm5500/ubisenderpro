package ci.ubipharm.crm.rest.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.ChiffrementService;
import ci.ubipharm.crm.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.rest.service.WebhookMetaService;
import ci.ubipharm.crm.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduit les notifications de Meta dans les evenements du service wa-web : memes accuses (distribue, lu), meme copie
 * des reponses des clients ; un message refuse apres coup par Meta passe en echec.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookMetaServiceImpl implements WebhookMetaService {

    private final NamedParameterJdbcTemplate jdbc;
    private final EvenementWhatsappService evenementWhatsappService;
    private final JsonMapper jsonMapper;
    private final ChiffrementService chiffrementService;

    @Override
    public String verifierAbonnement(String mode, String jeton, String defi) {
        String attendu = jdbc.queryForObject("select jeton_verification from crm_compte_meta where id = 1", Map.of(), String.class);
        boolean valide = "subscribe".equals(mode) && jeton != null && attendu != null
                && MessageDigest.isEqual(attendu.getBytes(StandardCharsets.UTF_8), jeton.getBytes(StandardCharsets.UTF_8));
        return valide ? defi : null;
    }

    @Override
    public void verifierSignature(String corps, String signature) {
        List<String> secrets = jdbc.queryForList("select secret_application from crm_compte_meta where id = 1 and secret_application <> ''",
                Map.of(), String.class);
        if (secrets.isEmpty() || secrets.getFirst() == null || signature == null || !signature.startsWith("sha256=")) {
            throw new BadCredentialsException("Notification Meta non signee ou secret d'application absent");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String secret = chiffrementService.dechiffrer(secrets.getFirst());
            if (secret == null) {
                throw new BadCredentialsException("Secret d'application Meta illisible");
            }
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] attendu = mac.doFinal(corps.getBytes(StandardCharsets.UTF_8));
            byte[] recu = HexFormat.of().parseHex(signature.substring("sha256=".length()));
            if (!MessageDigest.isEqual(attendu, recu)) {
                throw new BadCredentialsException("Signature Meta invalide");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BadCredentialsException("Signature Meta illisible", e);
        }
    }

    @Override
    public void traiter(String corps) {
        JsonNode racine = jsonMapper.readTree(corps);
        for (JsonNode entree : racine.path("entry")) {
            for (JsonNode changement : entree.path("changes")) {
                JsonNode valeur = changement.path("value");
                String numero = numero(valeur.path("metadata").path("phone_number_id").asString(""));
                if (numero == null) {
                    continue;
                }
                valeur.path("statuses").forEach(s -> statut(numero, s));
                String nom = valeur.path("contacts").path(0).path("profile").path("name").asString(null);
                valeur.path("messages").forEach(m -> message(numero, nom, m));
            }
        }
    }

    private void statut(String numero, JsonNode s) {
        String identifiant = s.path("id").asString("");
        switch (s.path("status").asString("")) {
            case "delivered" -> evenementWhatsappService.accuse(EvenementAccuseDTO.builder().sessionId(numero).id(identifiant).status("DISTRIBUE").build());
            case "read" -> evenementWhatsappService.accuse(EvenementAccuseDTO.builder().sessionId(numero).id(identifiant).status("LU").build());
            case "failed" -> {
                JsonNode erreur = s.path("errors").path(0);
                String details = erreur.path("error_data").path("details").asString("");
                String motif = !details.isBlank() ? details : erreur.path("title").asString(erreur.path("message").asString("Refusé par Meta"));
                evenementWhatsappService.echec(numero, identifiant, "Refusé par Meta : " + motif);
            }
            default -> {
                // sent : deja compte a l'envoi
            }
        }
    }

    private void message(String numero, String nom, JsonNode m) {
        String type = m.path("type").asString("text");
        String texte = switch (type) {
            case "text" -> m.path("text").path("body").asString(null);
            case "button" -> m.path("button").path("text").asString(null);
            case "interactive" -> m.path("interactive").path("button_reply").path("title")
                    .asString(m.path("interactive").path("list_reply").path("title").asString(null));
            case "image" -> m.path("image").path("caption").asString("[image]");
            case "video" -> m.path("video").path("caption").asString("[vidéo]");
            case "document" -> m.path("document").path("filename").asString("[document]");
            case "audio" -> "[audio]";
            default -> "[message]";
        };
        evenementWhatsappService.message(EvenementMessageDTO.builder()
                .sessionId(numero)
                .id(m.path("id").asString(null))
                .from(m.path("from").asString(null))
                .name(nom)
                .type(switch (type) {
                    case "text", "button", "interactive" -> "TEXTE";
                    case "image" -> "IMAGE";
                    case "audio" -> "AUDIO";
                    case "video" -> "VIDEO";
                    case "document" -> "DOCUMENT";
                    default -> "TEXTE";
                })
                .text(texte)
                .build());
    }

    /** Numero du CRM derriere l'identifiant Meta ; null pour un numero inconnu (autre application du compte). */
    private String numero(String phoneNumberId) {
        List<String> numeros = jdbc.queryForList("select numero from crm_session_whatsapp where meta_phone_number_id = :id and fournisseur = 'META'",
                Map.of("id", phoneNumberId), String.class);
        if (numeros.isEmpty()) {
            log.info("Notification Meta pour un numero inconnu : {}", phoneNumberId);
            return null;
        }
        return numeros.getFirst();
    }
}
