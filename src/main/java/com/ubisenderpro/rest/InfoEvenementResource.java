package com.ubisenderpro.rest;

import com.ubisenderpro.entity.InfoEvenement;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.InfoEvenementService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

@Path("/infos")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InfoEvenementResource {

    @EJB
    private InfoEvenementService service;

    @GET
    public List<InfoEvenement> lister(@QueryParam("type") String type,
                                      @QueryParam("types") String types,
                                      @QueryParam("statut") String statut,
                                      @QueryParam("encours") boolean encours,
                                      @QueryParam("historique") boolean historique) {
        if (historique) { return service.listerHistorique(); }
        if (encours) { return service.listerEnCours(); }
        if (statut != null && !statut.isEmpty()) { return service.listerParStatut(statut); }
        if (types != null && !types.isEmpty()) { return service.listerParTypes(Arrays.asList(types.split(","))); }
        if (type != null && !type.isEmpty()) { return service.listerParType(type); }
        return service.lister();
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return service.parId(id).map(i -> Response.ok(i).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(menu = "infos")
    public Response creer(InfoEvenement i) {
        return Response.status(Response.Status.CREATED).entity(service.creer(i)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "infos")
    public Response modifier(@PathParam("id") Long id, InfoEvenement i) {
        i.setId(id);
        return Response.ok(service.modifier(i)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "infos")
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/dupliquer")
    @Secured(menu = "infos")
    public Response dupliquer(@PathParam("id") Long id) {
        InfoEvenement c = service.dupliquer(id);
        return c == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.status(Response.Status.CREATED).entity(c).build();
    }

    @POST
    @Path("/{id}/programmer")
    @Secured(menu = "infos")
    public Response programmer(@PathParam("id") Long id) {
        InfoEvenement i = service.programmer(id);
        return i == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(i).build();
    }

    @POST
    @Path("/{id}/annuler")
    @Secured(menu = "infos")
    public Response annuler(@PathParam("id") Long id) {
        InfoEvenement i = service.annuler(id);
        return i == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(i).build();
    }

    @POST
    @Path("/{id}/archiver")
    @Secured(menu = "infos")
    public Response archiver(@PathParam("id") Long id) {
        InfoEvenement i = service.archiver(id);
        return i == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(i).build();
    }
}
