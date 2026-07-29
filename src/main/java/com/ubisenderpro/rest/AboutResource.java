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

    @javax.ws.rs.core.Context
    private javax.servlet.ServletContext servletContext;

    /**
     * Horodatage de compilation du livrable, lu dans le MANIFEST du WAR
     * (attribut {@code Build-Time} pose par Maven). Permet de verifier d'un
     * coup d'oeil qu'un WAR fraichement construit a bien ete redeploye —
     * un ancien WAR encore en place explique des correctifs « sans effet ».
     *
     * <p>Lecture via le ServletContext : elle vise le manifeste DU WAR. Un
     * simple getResourceAsStream sur le chargeur de classes pourrait renvoyer
     * le manifeste d'une bibliotheque embarquee, donc une date sans rapport.</p>
     */
    private String compileLe() {
        try {
            if (servletContext != null) {
                try (java.io.InputStream in = servletContext.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                    if (in != null) {
                        String v = new java.util.jar.Manifest(in).getMainAttributes().getValue("Build-Time");
                        if (v != null && !v.trim().isEmpty()) { return v.trim(); }
                    }
                }
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
