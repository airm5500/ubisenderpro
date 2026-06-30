package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ReferentielGeo;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ReferentielGeoService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Référentiels géographiques (pays, régions, villes, communes, agences).
 * Lecture pour alimenter les listes déroulantes ; gestion réservée à l'ADMIN.
 */
@Path("/referentiels")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReferentielGeoResource {

    @EJB
    private ReferentielGeoService service;

    @GET
    @Path("/{type}")
    public List<ReferentielGeo> lister(@PathParam("type") String type) {
        return service.lister(type);
    }

    @POST
    @Path("/{type}")
    @Secured(roles = {"ADMIN"})
    public Response creer(@PathParam("type") String type, ReferentielGeo r) {
        return Response.status(Response.Status.CREATED).entity(service.creer(type, r)).build();
    }

    @PUT
    @Path("/{type}/{id}")
    @Secured(roles = {"ADMIN"})
    public Response modifier(@PathParam("type") String type, @PathParam("id") Long id, ReferentielGeo r) {
        return Response.ok(service.modifier(id, r)).build();
    }

    @PUT
    @Path("/{type}/{id}/actif")
    @Secured(roles = {"ADMIN"})
    public Response definirActif(@PathParam("id") Long id, @QueryParam("actif") @DefaultValue("true") boolean actif) {
        service.definirActif(id, actif);
        return Response.noContent().build();
    }
}
