package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Promotion;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.PromotionService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/promotions")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PromotionResource {

    @EJB
    private PromotionService promotionService;

    @GET
    public List<Promotion> lister() { return promotionService.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return promotionService.parId(id).map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response creer(Promotion p) {
        try {
            return Response.status(Response.Status.CREATED).entity(promotionService.creer(p)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifier(@PathParam("id") Long id, Promotion p) {
        try {
            p.setId(id);
            return Response.ok(promotionService.modifier(p)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response supprimer(@PathParam("id") Long id) {
        promotionService.supprimer(id);
        return Response.noContent().build();
    }
}
