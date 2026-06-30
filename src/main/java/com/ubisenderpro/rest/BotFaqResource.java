package com.ubisenderpro.rest;

import com.ubisenderpro.entity.BotFaq;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.BotFaqService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * CRUD de la base de connaissance du bot (réservé aux administrateurs).
 */
@Path("/bot/faq")
@Secured(roles = {"ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BotFaqResource {

    @EJB
    private BotFaqService faqService;

    @GET
    public List<BotFaq> lister() {
        return faqService.lister();
    }

    @POST
    public Response creer(BotFaq f) {
        return Response.status(Response.Status.CREATED).entity(faqService.creer(f)).build();
    }

    @PUT
    @Path("/{id}")
    public Response modifier(@PathParam("id") Long id, BotFaq f) {
        BotFaq r = faqService.modifier(id, f);
        return r == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(r).build();
    }

    @DELETE
    @Path("/{id}")
    public Response supprimer(@PathParam("id") Long id) {
        faqService.supprimer(id);
        return Response.noContent().build();
    }
}
