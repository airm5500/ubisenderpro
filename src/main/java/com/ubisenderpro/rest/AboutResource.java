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
    public static final String APPLICATION = "UbiSenderPro";
    /** Version applicative (à mettre à jour à chaque livraison). */
    public static final String VERSION = "1.0.0";
    /** Développeur (renseigné une seule fois ici, rappelé dans « À propos »). */
    public static final String DEVELOPPEUR = "Franck NZI";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> about() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", APPLICATION);
        m.put("version", VERSION);
        m.put("developpeur", DEVELOPPEUR);
        return m;
    }
}
