package com.ubisenderpro.rest;

import com.ubisenderpro.entity.RecModele;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.RecModeleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Modèles de relance du module Recouvrement.
 */
@Path("/recouvrement/modeles")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecModeleResource {

    @EJB
    private RecModeleService service;

    @GET
    public List<RecModele> lister() { return service.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return service.parId(id).map(m -> Response.ok(m).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(menu = "recouvrement", action = "CREER")
    public Response creer(RecModele m) {
        return Response.status(Response.Status.CREATED).entity(service.creer(m)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "recouvrement", action = "MODIFIER")
    public Response modifier(@PathParam("id") Long id, RecModele m) {
        return Response.ok(service.modifier(id, m)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "recouvrement", action = "SUPPRIMER")
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }
}
