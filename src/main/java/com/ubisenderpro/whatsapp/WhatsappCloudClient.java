package com.ubisenderpro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.WhatsappAccount;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

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

    public static class UploadResult {
        public final boolean success;
        public final String mediaId;
        public final String erreur;

        public UploadResult(boolean success, String mediaId, String erreur) {
            this.success = success;
            this.mediaId = mediaId;
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
        return envoyerMedia(destinataire, type, "link", url, legende);
    }

    /**
     * Envoie un média déjà téléversé sur les serveurs de Meta, à partir de son media_id.
     * Cette voie évite l'hébergement public du fichier exigé par l'envoi par URL.
     */
    public SendResult envoyerMediaParId(String destinataire, String type, String mediaId, String legende) {
        return envoyerMedia(destinataire, type, "id", mediaId, legende);
    }

    private SendResult envoyerMedia(String destinataire, String type, String cle, String valeur, String legende) {
        Map<String, Object> body = baseBody(destinataire, type);
        Map<String, Object> media = new HashMap<>();
        media.put(cle, valeur);
        if (legende != null && ("image".equals(type) || "video".equals(type) || "document".equals(type))) {
            media.put("caption", legende);
        }
        body.put(type, media);
        return envoyer(body);
    }

    /**
     * Téléverse un fichier binaire vers l'API WhatsApp (endpoint /media) et renvoie le media_id.
     * Le fichier reste disponible une trentaine de jours côté Meta et peut être réutilisé
     * pour l'envoi de messages média sans hébergement public.
     */
    public UploadResult uploadMedia(byte[] contenu, String mimeType, String nomFichier) {
        if (account.isModeTest()) {
            return new UploadResult(true, "SIMULATED-MEDIA-" + UUID.randomUUID(), null);
        }
        HttpURLConnection conn = null;
        try {
            String endpoint = String.format("https://graph.facebook.com/%s/%s/media",
                    account.getApiVersion(), account.getPhoneNumberId());
            String boundary = "----UbiSenderPro" + UUID.randomUUID().toString().replace("-", "");
            byte[] payload = corpsMultipart(boundary, contenu, mimeType,
                    nomFichier == null || nomFichier.isEmpty() ? "fichier" : nomFichier);

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Authorization", "Bearer " + account.getAccessToken());
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            String reponse = lire(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 200 && code < 300) {
                JsonNode node = MAPPER.readTree(reponse);
                String id = node.path("id").asText(null);
                if (id == null || id.isEmpty()) {
                    return new UploadResult(false, null, "Réponse sans media_id : " + reponse);
                }
                return new UploadResult(true, id, null);
            }
            return new UploadResult(false, null, "HTTP " + code + " : " + reponse);
        } catch (Exception e) {
            return new UploadResult(false, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] corpsMultipart(String boundary, byte[] contenu, String mimeType, String nomFichier)
            throws Exception {
        String tiret = "--";
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write((tiret + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"messaging_product\"" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("whatsapp" + crlf).getBytes(StandardCharsets.UTF_8));

        out.write((tiret + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"type\"" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(((mimeType == null ? "application/octet-stream" : mimeType) + crlf)
                .getBytes(StandardCharsets.UTF_8));

        out.write((tiret + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + nomFichier + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + (mimeType == null ? "application/octet-stream" : mimeType) + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8));
        out.write(contenu);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write((tiret + boundary + tiret + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Envoie un message à partir d'un modèle approuvé par Meta (corps seul).
     */
    public SendResult envoyerModele(String destinataire, String nomModele, String langue, List<String> variables) {
        return envoyerModele(destinataire, nomModele, langue, variables, null, null);
    }

    /**
     * Envoie un modèle avec, en option, un média d'en-tête (IMAGE, VIDEO ou DOCUMENT)
     * fourni par URL publique.
     *
     * @param enteteMediaType IMAGE | VIDEO | DOCUMENT (ou null/TEXTE/AUCUN pour aucun média)
     * @param enteteMediaUrl  URL publique du média d'en-tête
     */
    public SendResult envoyerModele(String destinataire, String nomModele, String langue,
                                    List<String> variables, String enteteMediaType, String enteteMediaUrl) {
        Map<String, Object> body = baseBody(destinataire, "template");
        Map<String, Object> template = new HashMap<>();
        template.put("name", nomModele);
        Map<String, Object> language = new HashMap<>();
        language.put("code", langue == null ? "fr" : langue);
        template.put("language", language);

        List<Object> components = new java.util.ArrayList<>();

        // En-tête média éventuel.
        String type = enteteMediaType == null ? "" : enteteMediaType.trim().toUpperCase();
        if (enteteMediaUrl != null && !enteteMediaUrl.isEmpty()
                && (type.equals("IMAGE") || type.equals("VIDEO") || type.equals("DOCUMENT"))) {
            Map<String, Object> media = new HashMap<>();
            media.put("link", enteteMediaUrl);
            Map<String, Object> param = new HashMap<>();
            param.put("type", type.toLowerCase());
            param.put(type.toLowerCase(), media);
            Map<String, Object> header = new HashMap<>();
            header.put("type", "header");
            header.put("parameters", new Object[]{param});
            components.add(header);
        }

        if (variables != null && !variables.isEmpty()) {
            Map<String, Object> bodyComp = new HashMap<>();
            bodyComp.put("type", "body");
            Object[] params = variables.stream().map(v -> {
                Map<String, Object> p = new HashMap<>();
                p.put("type", "text");
                p.put("text", v);
                return p;
            }).toArray();
            bodyComp.put("parameters", params);
            components.add(bodyComp);
        }

        if (!components.isEmpty()) {
            template.put("components", components.toArray());
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
        if (account.isModeTest()) {
            // Mode test : aucun appel à Meta, on simule un envoi réussi.
            return new SendResult(true, "SIMULATED-" + UUID.randomUUID(), null);
        }
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
            return new SendResult(false, null, messageErreur(code, reponse));
        } catch (Exception e) {
            return new SendResult(false, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Extrait un message lisible de l'erreur Meta (code + libellé + détails). */
    private String messageErreur(int code, String reponse) {
        try {
            JsonNode err = MAPPER.readTree(reponse).path("error");
            if (!err.isMissingNode() && !err.isNull()) {
                int c = err.path("code").asInt(0);
                String msg = err.path("message").asText("");
                String details = err.path("error_data").path("details").asText("");
                StringBuilder sb = new StringBuilder();
                if (c > 0) { sb.append("Meta ").append(c).append(" : "); }
                sb.append(msg.isEmpty() ? ("HTTP " + code) : msg);
                if (!details.isEmpty()) { sb.append(" — ").append(details); }
                return sb.toString();
            }
        } catch (Exception ignore) { /* réponse non-JSON : on retombe sur le brut */ }
        return "HTTP " + code + " : " + reponse;
    }

    private String lire(java.io.InputStream is) {
        if (is == null) return "";
        try (Scanner s = new Scanner(is, StandardCharsets.UTF_8.name())) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }
}
