package com.ubisenderpro.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Informations « À propos » (version + développeur), en lecture seule.
 *
 * <p>Ces valeurs ne sont volontairement pas exposées dans l'écran des
 * paramètres : elles se modifient uniquement ici, dans le code (constantes),
 * et sont seulement consultables par l'utilisateur.</p>
 */
@Path("/about")
public class AboutResource {

    /** Nom de l'application. */
    public static final String APPLICATION = "UbiSmartCRM Pro";
    /** Version applicative (à mettre à jour à chaque livraison). */
    public static final String VERSION = "2.0.0";
    /** Développeur (renseigné une seule fois ici, rappelé dans « À propos »). */
    public static final String DEVELOPPEUR = "Hermann NZI";
    /** E-mail de contact du développeur. */
    public static final String EMAIL = "nzifranck13@gmail.com";

    /**
     * Horodatage de compilation du livrable, lu dans {@code build.properties}
     * genere par Maven (filtrage de ressources). Permet de verifier d'un coup
     * d'oeil qu'un WAR fraichement construit a bien ete redeploye — un ancien
     * WAR encore en place explique des correctifs restes « sans effet ».
     *
     * <p>Une ressource qui nous appartient (WEB-INF/classes) est lue ici plutot
     * que le MANIFEST : ce dernier n'est pas expose de la meme facon selon le
     * serveur, et un chargeur de classes peut renvoyer celui d'une
     * bibliotheque embarquee — donc une date sans aucun rapport.</p>
     */
    private String compileLe() {
        try (java.io.InputStream in = AboutResource.class.getResourceAsStream("/build.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                String v = p.getProperty("build.time");
                // Si le filtrage n'a pas eu lieu, la valeur brute reste « ${...} ».
                if (v != null && !v.trim().isEmpty() && !v.contains("${")) { return v.trim(); }
            }
        } catch (Exception ignore) { /* information de confort : jamais bloquante */ }
        return "inconnu";
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> about() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", APPLICATION);
        m.put("version", VERSION);
        m.put("developpeur", DEVELOPPEUR);
        m.put("email", EMAIL);
        m.put("compileLe", compileLe());
        return m;
    }
}
