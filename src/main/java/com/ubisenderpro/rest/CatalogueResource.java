package com.ubisenderpro.rest;

import com.ubisenderpro.entity.CategorieArticle;
import com.ubisenderpro.entity.Marque;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.CatalogueService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/catalogue")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CatalogueResource {

    @EJB
    private CatalogueService catalogueService;

    @GET
    @Path("/categories")
    public List<CategorieArticle> categories() { return catalogueService.listerCategories(); }

    @POST
    @Path("/categories")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response creerCategorie(CategorieArticle c) {
        return Response.status(Response.Status.CREATED).entity(catalogueService.creerCategorie(c)).build();
    }

    @PUT
    @Path("/categories/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifierCategorie(@PathParam("id") Long id, CategorieArticle c) {
        c.setId(id);
        return Response.ok(catalogueService.modifierCategorie(c)).build();
    }

    @GET
    @Path("/marques")
    public List<Marque> marques() { return catalogueService.listerMarques(); }

    @POST
    @Path("/marques")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response creerMarque(Marque m) {
        return Response.status(Response.Status.CREATED).entity(catalogueService.creerMarque(m)).build();
    }

    @PUT
    @Path("/marques/{id}")
    @Secured(roles = {"ADMIN", "CATALOGUE"})
    public Response modifierMarque(@PathParam("id") Long id, Marque m) {
        m.setId(id);
        return Response.ok(catalogueService.modifierMarque(m)).build();
    }
}
