package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ContactService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Path("/contacts")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContactResource {

    @EJB
    private ContactService contactService;

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        Optional<ClientContact> contact = contactService.parId(id);
        return contact.map(c -> Response.ok(c).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    public Response creer(ClientContact contact) {
        return Response.status(Response.Status.CREATED).entity(contactService.creer(contact)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    public Response modifier(@PathParam("id") Long id, ClientContact contact) {
        if (!contactService.parId(id).isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        contact.setId(id);
        return Response.ok(contactService.modifier(contact)).build();
    }

    @POST
    @Path("/{id}/unsubscribe")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    public Response desabonner(@PathParam("id") Long id) {
        contactService.definirDesabonnement(id, true);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/subscribe")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR"})
    public Response reabonner(@PathParam("id") Long id) {
        contactService.definirDesabonnement(id, false);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN"})
    public Response supprimer(@PathParam("id") Long id) {
        contactService.supprimer(id);
        return Response.noContent().build();
    }
}
