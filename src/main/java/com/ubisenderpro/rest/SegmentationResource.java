package com.ubisenderpro.rest;

import com.ubisenderpro.entity.SegmentationClient;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.SegmentationService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/segmentations")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SegmentationResource {

    @EJB
    private SegmentationService segmentationService;

    @GET
    public List<SegmentationClient> lister() {
        return segmentationService.lister();
    }

    @POST
    @Secured(menu = "clients")
    public Response creer(SegmentationClient s) {
        try {
            return Response.status(Response.Status.CREATED).entity(segmentationService.creer(s)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "clients")
    public Response modifier(@PathParam("id") Long id, SegmentationClient s) {
        try {
            return Response.ok(segmentationService.modifier(id, s)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "clients")
    public Response supprimer(@PathParam("id") Long id) {
        try {
            segmentationService.supprimer(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("erreur", e.getMessage())).build();
        }
    }
}
