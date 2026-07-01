package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.ListeDiffusion;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ListeService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/lists")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListeResource {

    @EJB
    private ListeService listeService;

    @GET
    public List<ListeDiffusion> lister() { return listeService.lister(); }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(ListeDiffusion l) {
        return Response.status(Response.Status.CREATED).entity(listeService.creer(l)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, ListeDiffusion l) {
        l.setId(id);
        return Response.ok(listeService.modifier(l)).build();
    }

    @GET
    @Path("/{id}/contacts")
    public List<ClientContact> contacts(@PathParam("id") Long id) {
        return listeService.contacts(id);
    }

    @POST
    @Path("/{id}/contacts")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response ajouterContact(@PathParam("id") Long id, Map<String, Object> body) {
        Long contactId = Long.valueOf(String.valueOf(body.get("contactId")));
        String source = body.get("source") == null ? "MANUEL" : String.valueOf(body.get("source"));
        listeService.ajouterContact(id, contactId, source);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/contacts/{contactId}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response retirerContact(@PathParam("id") Long id, @PathParam("contactId") Long contactId) {
        listeService.retirerContact(id, contactId);
        return Response.noContent().build();
    }
}
