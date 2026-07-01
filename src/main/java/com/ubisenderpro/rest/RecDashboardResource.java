package com.ubisenderpro.rest;

import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.RecDashboardService;
import com.ubisenderpro.service.RecScopeService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * Tableaux de bord du module Recouvrement (groupe + point par agence),
 * cloisonnés selon l'agence de l'utilisateur (sauf vue consolidée / ADMIN).
 */
@Path("/recouvrement/dashboard")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecDashboardResource {

    @EJB
    private RecDashboardService dashboardService;
    @EJB
    private RecScopeService scope;
    @Inject
    private SessionStore sessionStore;

    @GET
    @Path("/groupe")
    public Map<String, Object> groupe(@HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        return dashboardService.groupe(portee(auth));
    }

    @GET
    @Path("/par-agence")
    public List<Map<String, Object>> parAgence(@HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        return dashboardService.parAgence(portee(auth));
    }

    private String portee(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) { return null; }
        AuthenticatedUser u = sessionStore.validate(auth.substring("Bearer ".length()).trim());
        return scope.agencePortee(u);
    }
}
