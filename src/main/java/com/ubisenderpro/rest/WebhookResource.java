package com.ubisenderpro.rest;

import com.ubisenderpro.service.WebhookService;

import javax.ejb.EJB;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Webhook WhatsApp Cloud API (section 28.12 de la spec).
 * Cette ressource n'est pas protégée par session : elle est appelée par Meta.
 * La vérification GET compare hub.verify_token au jeton enregistré sur un compte.
 */
@Path("/webhooks/whatsapp")
public class WebhookResource {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private WebhookService webhookService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response verifier(@QueryParam("hub.mode") String mode,
                             @QueryParam("hub.verify_token") String verifyToken,
                             @QueryParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && verifyToken != null && tokenValide(verifyToken)) {
            return Response.ok(challenge).build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response recevoir(String payload) {
        // On répond 200 immédiatement (Meta réessaie sinon) ; le traitement persiste l'événement.
        try {
            webhookService.traiter(payload);
        } catch (Exception ignored) {
            // L'événement brut est conservé pour rejouer en cas d'erreur.
        }
        return Response.ok("{\"status\":\"received\"}").build();
    }

    private boolean tokenValide(String verifyToken) {
        List<?> r = em.createNativeQuery(
                "SELECT 1 FROM usp_whatsapp_account WHERE verify_token = ?1 AND actif = 1")
                .setParameter(1, verifyToken).getResultList();
        return !r.isEmpty();
    }
}
