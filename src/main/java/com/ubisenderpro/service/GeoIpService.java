package com.ubisenderpro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.ConnexionLog;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Géolocalisation approximative d'une adresse IP (best-effort, asynchrone) pour
 * renseigner le « lieu » d'une connexion. Utilise le service public ip-api.com.
 * Échoue silencieusement si le réseau sortant n'est pas autorisé.
 */
@Stateless
public class GeoIpService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Renseigne le lieu de la connexion de façon asynchrone (ne bloque pas le login). */
    @Asynchronous
    public void localiser(Long connexionLogId, String ip) {
        if (connexionLogId == null) { return; }
        String lieu = resoudre(ip);
        if (lieu == null || lieu.isEmpty()) { return; }
        enregistrer(connexionLogId, lieu);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void enregistrer(Long id, String lieu) {
        try {
            ConnexionLog c = em.find(ConnexionLog.class, id);
            if (c != null) { c.setLieu(lieu); em.merge(c); }
        } catch (Exception ignore) { /* non bloquant */ }
    }

    /** @return "Ville, Pays" ou "Réseau local" ; null si indéterminé. */
    private String resoudre(String ip) {
        if (ip == null || ip.isEmpty() || estLocale(ip)) { return "Réseau local"; }
        HttpURLConnection con = null;
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=status,country,regionName,city");
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(2500);
            con.setReadTimeout(2500);
            con.setRequestMethod("GET");
            if (con.getResponseCode() != 200) { return null; }
            try (InputStream is = con.getInputStream()) {
                JsonNode n = MAPPER.readTree(is);
                if (!"success".equals(n.path("status").asText())) { return null; }
                String ville = n.path("city").asText("");
                String region = n.path("regionName").asText("");
                String pays = n.path("country").asText("");
                StringBuilder sb = new StringBuilder();
                if (!ville.isEmpty()) { sb.append(ville); }
                else if (!region.isEmpty()) { sb.append(region); }
                if (!pays.isEmpty()) { sb.append(sb.length() > 0 ? ", " : "").append(pays); }
                return sb.length() > 0 ? sb.toString() : null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) { con.disconnect(); }
        }
    }

    private boolean estLocale(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equalsIgnoreCase("localhost")) { return true; }
        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("169.254.")
                || ip.startsWith("fe80:") || ip.startsWith("fc") || ip.startsWith("fd")) { return true; }
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) { return true; }
            } catch (Exception ignore) { /* format inattendu */ }
        }
        return false;
    }
}
