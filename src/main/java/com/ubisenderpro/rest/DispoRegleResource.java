package com.ubisenderpro.rest;

import com.ubisenderpro.entity.DispoRegle;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.DispoRegleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/dispo-regles")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DispoRegleResource {

    @EJB
    private DispoRegleService service;

    @GET
    public List<DispoRegle> lister() { return service.lister(); }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response creer(DispoRegle r) {
        return Response.status(Response.Status.CREATED).entity(service.creer(r)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response modifier(@PathParam("id") Long id, DispoRegle r) {
        r.setId(id);
        return Response.ok(service.modifier(r)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING", "CATALOGUE"})
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }
}
