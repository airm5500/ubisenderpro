package com.ubisenderpro.security;

/**
 * Contexte de la requête HTTP courante (adresse IP + nom de poste), capturé par
 * l'AuthenticationFilter pour être disponible dans les services (journalisation).
 * Stocké en ThreadLocal : valable le temps du traitement d'une requête sécurisée.
 */
public final class RequestContext {

    private static final ThreadLocal<String[]> COURANT = new ThreadLocal<>();

    private RequestContext() { }

    public static void definir(String ip, String poste) {
        COURANT.set(new String[]{ ip, poste });
    }

    public static String ip() {
        String[] v = COURANT.get();
        return v == null ? null : v[0];
    }

    public static String poste() {
        String[] v = COURANT.get();
        return v == null ? null : v[1];
    }

    public static void effacer() {
        COURANT.remove();
    }
}
