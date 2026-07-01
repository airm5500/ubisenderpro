package com.ubisenderpro.rest;

import com.ubisenderpro.entity.RecReferentiel;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.RecReferentielService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Référentiels du module Recouvrement (segments commerciaux, profils de
 * paiement, statuts). Lecture pour tout utilisateur du module ; gestion
 * réservée au privilège GERER_REFERENTIELS.
 */
@Path("/recouvrement/referentiels")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecReferentielResource {

    @EJB
    private RecReferentielService service;

    @GET
    @Path("/{type}")
    public List<RecReferentiel> lister(@PathParam("type") String type) {
        return service.lister(type);
    }

    @POST
    @Path("/{type}")
    @Secured(menu = "recouvrement", action = "GERER_REFERENTIELS")
    public Response creer(@PathParam("type") String type, RecReferentiel r) {
        return Response.status(Response.Status.CREATED).entity(service.creer(type, r)).build();
    }

    @PUT
    @Path("/{type}/{id}")
    @Secured(menu = "recouvrement", action = "GERER_REFERENTIELS")
    public Response modifier(@PathParam("id") Long id, RecReferentiel r) {
        return Response.ok(service.modifier(id, r)).build();
    }

    @PUT
    @Path("/{type}/{id}/actif")
    @Secured(menu = "recouvrement", action = "GERER_REFERENTIELS")
    public Response actif(@PathParam("id") Long id, @QueryParam("actif") @DefaultValue("true") boolean actif) {
        service.definirActif(id, actif);
        return Response.noContent().build();
    }

    @POST
    @Path("/{type}/import")
    @Secured(menu = "recouvrement", action = "IMPORTER")
    public Response importer(@PathParam("type") String type, Map<String, String> body) {
        int crees = service.importer(type, body == null ? null : body.get("contenu"));
        return Response.ok(Collections.singletonMap("crees", crees)).build();
    }

    /** Import via l'assistant à mapping de colonnes (fichier CSV/Excel + correspondance). */
    @POST
    @Path("/{type}/import-assistant")
    @Secured(menu = "recouvrement", action = "IMPORTER")
    public Response importAssistant(@PathParam("type") String type,
                                    com.ubisenderpro.dto.ImportClientRequest req) {
        return Response.ok(service.importerAssistant(type, req)).build();
    }
}
