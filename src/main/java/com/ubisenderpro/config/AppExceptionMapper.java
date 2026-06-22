package com.ubisenderpro.config;

import com.ubisenderpro.service.AuditService;
import com.ubisenderpro.service.ValidationException;

import javax.ejb.EJB;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mapper d'exceptions global : transforme toute erreur non gérée en réponse JSON
 * exploitable et la consigne précisément pour le dépannage (#3).
 *
 * <ul>
 *   <li><b>Logs techniques</b> : message précis avec chemin et champ fautif, pour
 *       que l'informaticien identifie la cause en un coup d'œil.</li>
 *   <li><b>Journal applicatif</b> : message clair et lisible par un utilisateur
 *       lambda (consultable dans Utilisateurs &gt; Journal d'actions).</li>
 *   <li><b>Réponse client</b> : {@code {erreur, champ}} pour afficher un message
 *       compréhensible et surligner le champ concerné.</li>
 * </ul>
 */
@Provider
public class AppExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(AppExceptionMapper.class.getName());

    @EJB
    private AuditService auditService;
    @Context
    private HttpHeaders headers;
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        // Respecte les réponses JAX-RS explicites (404 introuvable, 401 non authentifié, ...).
        if (ex instanceof WebApplicationException) {
            return ((WebApplicationException) ex).getResponse();
        }

        String chemin = uriInfo != null ? uriInfo.getPath() : "?";
        String auth = headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null;

        ValidationException ve = chercher(ex, ValidationException.class);
        IllegalArgumentException iae = ve == null ? chercher(ex, IllegalArgumentException.class) : null;

        if (ve != null || iae != null) {
            String champ = ve != null ? ve.getChamp() : null;
            String message = (ve != null ? ve.getMessage() : iae.getMessage());
            if (message == null || message.isEmpty()) { message = "Données invalides."; }

            // Log technique précis (champ + raison) pour faciliter le dépannage.
            LOG.warning("VALIDATION_KO chemin=/" + chemin
                    + (champ != null ? " champ=" + champ : "") + " raison=\"" + message + "\"");
            // Journal lisible pour un utilisateur non informaticien.
            auditService.tracer(auth, "VALIDATION_REFUS", "Saisie", null, message);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("erreur", message);
            if (champ != null) { body.put("champ", champ); }
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON).entity(body).build();
        }

        // Erreur inattendue : trace complète côté serveur, message générique côté client.
        LOG.log(Level.SEVERE, "ERREUR_SERVEUR chemin=/" + chemin + " : " + ex.getMessage(), ex);
        auditService.tracer(auth, "ERREUR_SERVEUR", "Système", null,
                "Erreur technique : " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("erreur", "Une erreur technique est survenue. Le détail a été consigné dans le journal.");
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    /** Remonte la chaîne des causes pour retrouver une exception du type voulu (déballe EJBException). */
    @SuppressWarnings("unchecked")
    private <T extends Throwable> T chercher(Throwable ex, Class<T> type) {
        Throwable courant = ex;
        int garde = 0;
        while (courant != null && garde++ < 12) {
            if (type.isInstance(courant)) { return (T) courant; }
            courant = courant.getCause();
        }
        return null;
    }
}
