package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Automatisation;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.AutomatisationService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/automations")
@Secured(roles = {"ADMIN", "MARKETING"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AutomatisationResource {

    @EJB
    private AutomatisationService automatisationService;

    @GET
    public List<Automatisation> lister() { return automatisationService.lister(); }

    @POST
    public Response creer(Automatisation a) {
        return Response.status(Response.Status.CREATED).entity(automatisationService.creer(a)).build();
    }

    @PUT
    @Path("/{id}")
    public Response modifier(@PathParam("id") Long id, Automatisation a) {
        a.setId(id);
        return Response.ok(automatisationService.modifier(a)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response supprimer(@PathParam("id") Long id) {
        automatisationService.supprimer(id);
        return Response.noContent().build();
    }
}
