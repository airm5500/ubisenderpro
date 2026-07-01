package com.ubisenderpro.rest;

import com.ubisenderpro.entity.EnvoiPropose;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.EnvoiProposeService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
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
    @Secured(menu = "marketing")
    public Response generer() {
        // Renvoie aussi la liste des propositions nouvellement créées (aperçu / sélection).
        return Response.ok(service.genererAvecApercu()).build();
    }

    /** Rejette en lot les propositions non conservées après l'aperçu de génération. */
    @POST
    @Path("/rejeter-lot")
    @Secured(menu = "marketing")
    public Response rejeterLot(Map<String, Object> body) {
        List<Long> ids = new java.util.ArrayList<>();
        Object raw = body == null ? null : body.get("ids");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Number) { ids.add(((Number) o).longValue()); }
                else if (o != null) { try { ids.add(Long.valueOf(o.toString())); } catch (NumberFormatException ignore) { } }
            }
        }
        String motif = body == null ? null : (String) body.get("motif");
        return Response.ok(Map.of("rejetes", service.rejeterPlusieurs(ids, motif))).build();
    }

    @POST
    @Path("/{id}/valider")
    @Secured(menu = "marketing")
    public Response valider(@PathParam("id") Long id, Map<String, Object> body, @Context UriInfo uriInfo) {
        try {
            Long listeId = nombre(body, "listeId");
            Long segmentId = nombre(body, "segmentId");
            EnvoiPropose e = service.valider(id, listeId, segmentId, uriInfo.getBaseUri().toString());
            return Response.ok(e).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", ex.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/rejeter")
    @Secured(menu = "marketing")
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
