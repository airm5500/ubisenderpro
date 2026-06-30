package com.ubisenderpro.rest;

import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.RecAssistantService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Assistant de relance : propositions à valider / rejeter.
 */
@Path("/recouvrement/propositions")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecPropositionResource {

    @EJB
    private RecAssistantService service;
    @Inject
    private SessionStore sessionStore;

    @GET
    public List<Map<String, Object>> lister(@QueryParam("statut") String statut) {
        return service.lister(statut);
    }

    @POST
    @Path("/generer")
    @Secured(menu = "recouvrement", action = "CREER")
    public Response generer() {
        int n = service.genererPropositions();
        return Response.ok(Collections.singletonMap("crees", n)).build();
    }

    @POST
    @Path("/{id}/valider")
    @Secured(menu = "recouvrement", action = "ENVOYER")
    public Response valider(@PathParam("id") Long id, Map<String, Object> body,
                            @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long modeleId = nombre(body, "modeleId");
        String canal = body == null || body.get("canal") == null ? null : String.valueOf(body.get("canal"));
        AuthenticatedUser u = utilisateur(authHeader);
        service.valider(id, modeleId, canal, u == null ? null : u.getId(), u == null ? null : u.getLogin());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/rejeter")
    @Secured(menu = "recouvrement", action = "MODIFIER")
    public Response rejeter(@PathParam("id") Long id) {
        service.rejeter(id);
        return Response.noContent().build();
    }

    private AuthenticatedUser utilisateur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        return sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
    }

    private static Long nombre(Map<String, Object> body, String cle) {
        if (body == null || body.get(cle) == null) { return null; }
        Object v = body.get(cle);
        if (v instanceof Number) { return ((Number) v).longValue(); }
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
