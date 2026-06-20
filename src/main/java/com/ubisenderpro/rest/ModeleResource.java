package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ModeleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/templates")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModeleResource {

    @EJB
    private ModeleService modeleService;

    @GET
    public List<ModeleMessage> lister() { return modeleService.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return modeleService.parId(id).map(m -> Response.ok(m).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(ModeleMessage m) {
        return Response.status(Response.Status.CREATED).entity(modeleService.creer(m)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, ModeleMessage m) {
        m.setId(id);
        return Response.ok(modeleService.modifier(m)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response supprimer(@PathParam("id") Long id) {
        modeleService.supprimer(id);
        return Response.noContent().build();
    }
}
