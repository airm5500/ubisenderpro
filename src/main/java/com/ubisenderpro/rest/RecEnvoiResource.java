package com.ubisenderpro.rest;

import com.ubisenderpro.entity.RecEnvoi;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.RecEnvoiService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Envoi de relances + historique du module Recouvrement.
 */
@Path("/recouvrement/envois")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecEnvoiResource {

    @EJB
    private RecEnvoiService service;
    @Inject
    private SessionStore sessionStore;

    @GET
    public List<RecEnvoi> historique(@QueryParam("clientId") Long clientId) {
        return service.historique(clientId);
    }

    @POST
    @Secured(menu = "recouvrement", action = "ENVOYER")
    public Response envoyer(Map<String, Object> body, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long clientId = nombre(body, "clientId");
        Long modeleId = nombre(body, "modeleId");
        String canal = body == null || body.get("canal") == null ? null : String.valueOf(body.get("canal"));
        AuthenticatedUser u = utilisateur(authHeader);
        RecEnvoi e = service.envoyer(clientId, modeleId, canal, u == null ? null : u.getId(),
                u == null ? null : u.getLogin());
        return Response.ok(e).build();
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
