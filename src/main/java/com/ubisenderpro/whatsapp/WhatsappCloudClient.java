package com.ubisenderpro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.WhatsappAccount;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Client de la WhatsApp Business Cloud API (Meta Graph API).
 * Envoi de messages texte, média et modèles. Lecture de l'identifiant de message
 * renvoyé par Meta afin de suivre les statuts via les webhooks.
 *
 * Référence : https://developers.facebook.com/docs/whatsapp/cloud-api
 */
public class WhatsappCloudClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WhatsappAccount account;

    public WhatsappCloudClient(WhatsappAccount account) {
        this.account = account;
    }

    public static class SendResult {
        public final boolean success;
        public final String waMessageId;
        public final String erreur;

        public SendResult(boolean success, String waMessageId, String erreur) {
            this.success = success;
            this.waMessageId = waMessageId;
            this.erreur = erreur;
        }
    }

    /** Envoie un message texte simple (fenêtre de service de 24h requise). */
    public SendResult envoyerTexte(String destinataire, String texte) {
        Map<String, Object> body = baseBody(destinataire, "text");
        Map<String, Object> text = new HashMap<>();
        text.put("preview_url", false);
        text.put("body", texte);
        body.put("text", text);
        return envoyer(body);
    }

    /** Envoie un média par URL (image, video, document, audio). */
    public SendResult envoyerMedia(String destinataire, String type, String url, String legende) {
        Map<String, Object> body = baseBody(destinataire, type);
        Map<String, Object> media = new HashMap<>();
        media.put("link", url);
        if (legende != null && ("image".equals(type) || "video".equals(type) || "document".equals(type))) {
            media.put("caption", legende);
        }
        body.put(type, media);
        return envoyer(body);
    }

    /**
     * Envoie un message à partir d'un modèle approuvé par Meta.
     * Les variables du corps sont passées dans l'ordre.
     */
    public SendResult envoyerModele(String destinataire, String nomModele, String langue, List<String> variables) {
        Map<String, Object> body = baseBody(destinataire, "template");
        Map<String, Object> template = new HashMap<>();
        template.put("name", nomModele);
        Map<String, Object> language = new HashMap<>();
        language.put("code", langue == null ? "fr" : langue);
        template.put("language", language);

        if (variables != null && !variables.isEmpty()) {
            Map<String, Object> component = new HashMap<>();
            component.put("type", "body");
            Object[] params = variables.stream().map(v -> {
                Map<String, Object> p = new HashMap<>();
                p.put("type", "text");
                p.put("text", v);
                return p;
            }).toArray();
            component.put("parameters", params);
            template.put("components", new Object[]{component});
        }
        body.put("template", template);
        return envoyer(body);
    }

    private Map<String, Object> baseBody(String destinataire, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", destinataire);
        body.put("type", type);
        return body;
    }

    private SendResult envoyer(Map<String, Object> body) {
        HttpURLConnection conn = null;
        try {
            String endpoint = String.format("https://graph.facebook.com/%s/%s/messages",
                    account.getApiVersion(), account.getPhoneNumberId());
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Authorization", "Bearer " + account.getAccessToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            byte[] payload = MAPPER.writeValueAsBytes(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            String reponse = lire(code >= 400 ? conn.getErrorStream() : conn.getInputStream());

            if (code >= 200 && code < 300) {
                JsonNode node = MAPPER.readTree(reponse);
                String id = node.path("messages").path(0).path("id").asText(null);
                return new SendResult(true, id, null);
            }
            return new SendResult(false, null, "HTTP " + code + " : " + reponse);
        } catch (Exception e) {
            return new SendResult(false, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String lire(java.io.InputStream is) {
        if (is == null) return "";
        try (Scanner s = new Scanner(is, StandardCharsets.UTF_8.name())) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }
}
