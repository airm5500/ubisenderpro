package com.ubisenderpro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.config.WaWebConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Client du service compagnon WhatsApp Web (Node/Baileys).
 * Canal NON officiel — voir wa-web/README.md.
 */
public class WaWebClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class SendResult {
        public final boolean success;
        public final String id;
        public final String erreur;
        public SendResult(boolean success, String id, String erreur) {
            this.success = success; this.id = id; this.erreur = erreur;
        }
    }

    /** Démarre/relance une session et renvoie son état (status, qr). */
    public JsonNode start(String sessionId) {
        return appel("POST", "/sessions/" + sessionId + "/start", null);
    }

    public JsonNode status(String sessionId) {
        return appel("GET", "/sessions/" + sessionId + "/status", null);
    }

    public void logout(String sessionId) {
        appel("POST", "/sessions/" + sessionId + "/logout", null);
    }

    public SendResult sendText(String sessionId, String to, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        body.put("text", text);
        return envoiResultat("/sessions/" + sessionId + "/send", body);
    }

    public SendResult sendMedia(String sessionId, String to, String type, String mediaUrl,
                                String caption, String mimeType, String fileName) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        body.put("type", type);
        body.put("mediaUrl", mediaUrl);
        if (caption != null) body.put("caption", caption);
        if (mimeType != null) body.put("mimeType", mimeType);
        if (fileName != null) body.put("fileName", fileName);
        return envoiResultat("/sessions/" + sessionId + "/send-media", body);
    }

    public JsonNode checkNumbers(String sessionId, List<String> numbers) {
        Map<String, Object> body = new HashMap<>();
        body.put("numbers", numbers);
        return appel("POST", "/sessions/" + sessionId + "/check-numbers", body);
    }

    private SendResult envoiResultat(String path, Map<String, Object> body) {
        try {
            JsonNode node = appel("POST", path, body);
            boolean ok = node != null && node.path("success").asBoolean(false);
            if (ok) return new SendResult(true, node.path("id").asText(null), null);
            String err = node == null ? "Réponse vide" : node.path("erreur").asText("Echec d'envoi");
            return new SendResult(false, null, err);
        } catch (Exception e) {
            return new SendResult(false, null, e.getMessage());
        }
    }

    private JsonNode appel(String method, String path, Map<String, Object> body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(WaWebConfig.url() + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("X-Api-Token", WaWebConfig.token());
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                byte[] payload = MAPPER.writeValueAsBytes(body);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
            }
            int code = conn.getResponseCode();
            String rep = lire(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return rep.isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(rep);
        } catch (Exception e) {
            return MAPPER.createObjectNode().put("erreur", "WA-Web injoignable : " + e.getMessage());
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
