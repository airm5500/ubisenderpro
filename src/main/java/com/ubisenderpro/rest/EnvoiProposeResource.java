package com.ubisenderpro.rest;

import com.ubisenderpro.entity.EnvoiPropose;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.EnvoiProposeService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Calendrier marketing : consultation des propositions d'envoi et décisions
 * humaines (validation -> campagne brouillon, rejet). La génération automatique
 * est portée par l'ordonnanceur ; l'endpoint {@code /generer} permet un
 * déclenchement manuel.
 */
@Path("/propositions")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnvoiProposeResource {

    @EJB
    private EnvoiProposeService service;

    @GET
    public List<EnvoiPropose> lister(@QueryParam("statut") String statut) {
        return service.lister(statut);
    }

    @POST
    @Path("/generer")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response generer() {
        int crees = service.genererPropositions();
        int expirees = service.expirerDepassees();
        return Response.ok(Map.of("crees", crees, "expirees", expirees)).build();
    }

    @POST
    @Path("/{id}/valider")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response valider(@PathParam("id") Long id, Map<String, Object> body) {
        try {
            Long listeId = nombre(body, "listeId");
            Long segmentId = nombre(body, "segmentId");
            EnvoiPropose e = service.valider(id, listeId, segmentId);
            return Response.ok(e).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", ex.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/rejeter")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response rejeter(@PathParam("id") Long id, Map<String, Object> body) {
        try {
            String motif = body == null ? null : (String) body.get("motif");
            return Response.ok(service.rejeter(id, motif)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", ex.getMessage())).build();
        }
    }

    private static Long nombre(Map<String, Object> body, String cle) {
        if (body == null) { return null; }
        Object v = body.get(cle);
        if (v == null) { return null; }
        if (v instanceof Number) { return ((Number) v).longValue(); }
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
