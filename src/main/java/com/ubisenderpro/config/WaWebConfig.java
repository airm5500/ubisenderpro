package com.ubisenderpro.config;

/**
 * Configuration d'accès au service compagnon WhatsApp Web (Node/Baileys).
 * Renseignée par variables d'environnement côté Payara :
 *   WA_WEB_URL   (défaut http://localhost:3000)
 *   WA_WEB_TOKEN (token partagé, en-tête X-Api-Token)
 */
public final class WaWebConfig {

    private WaWebConfig() { }

    public static String url() {
        String v = conf("WA_WEB_URL");
        return (v == null || v.isEmpty()) ? "http://localhost:3000" : v.replaceAll("/+$", "");
    }

    public static String token() {
        String v = conf("WA_WEB_TOKEN");
        return v == null ? "" : v;
    }

    /** Variable d'environnement OS, sinon propriété JVM (-Dxxx) — pratique sur Payara. */
    private static String conf(String cle) {
        String v = System.getenv(cle);
        if (v == null || v.isEmpty()) { v = System.getProperty(cle); }
        return v;
    }
}
