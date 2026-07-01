package com.ubisenderpro.rest;

import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.RecImportService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Import CSV du module Recouvrement (fiches d'initialisation, créances, règlements).
 */
@Path("/recouvrement/import")
@Secured(menu = "recouvrement", action = "IMPORTER")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecImportResource {

    @EJB
    private RecImportService service;

    @POST
    @Path("/fiches")
    public Response fiches(Map<String, String> body) {
        return Response.ok(service.importerFiches(contenu(body))).build();
    }

    @POST
    @Path("/creances")
    public Response creances(Map<String, String> body) {
        return Response.ok(service.importerCreances(contenu(body))).build();
    }

    @POST
    @Path("/paiements")
    public Response paiements(Map<String, String> body) {
        return Response.ok(service.importerPaiements(contenu(body))).build();
    }

    /* Variantes « assistant » : fichier CSV/Excel + mapping de colonnes. */
    @POST
    @Path("/fiches-assistant")
    public Response fichesAssistant(com.ubisenderpro.dto.ImportClientRequest req) {
        return Response.ok(service.importerFichesAssistant(req)).build();
    }

    @POST
    @Path("/creances-assistant")
    public Response creancesAssistant(com.ubisenderpro.dto.ImportClientRequest req) {
        return Response.ok(service.importerCreancesAssistant(req)).build();
    }

    @POST
    @Path("/paiements-assistant")
    public Response paiementsAssistant(com.ubisenderpro.dto.ImportClientRequest req) {
        return Response.ok(service.importerPaiementsAssistant(req)).build();
    }

    private String contenu(Map<String, String> body) {
        return body == null ? null : body.get("contenu");
    }
}
