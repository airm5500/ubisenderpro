package com.ubisenderpro.rest;

import com.ubisenderpro.entity.RecFiche;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.RecFicheService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Fiches recouvrement (complément financier par client) + situation (solde).
 */
@Path("/recouvrement/fiches")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecFicheResource {

    @EJB
    private RecFicheService service;

    @GET
    public List<Map<String, Object>> lister(@QueryParam("q") String q,
                                            @QueryParam("agence") String agence,
                                            @QueryParam("segment") String segment,
                                            @QueryParam("profil") String profil) {
        return service.listerAvecSolde(q, agence, segment, profil);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return service.parId(id).map(f -> Response.ok(f).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/client/{clientId}")
    public Response parClient(@PathParam("clientId") Long clientId) {
        return service.parClient(clientId).map(f -> Response.ok(f).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/client/{clientId}/situation")
    public Map<String, Object> situation(@PathParam("clientId") Long clientId) {
        return service.situation(clientId);
    }

    @POST
    @Secured(menu = "recouvrement", action = "CREER")
    public Response creer(RecFiche f) {
        return Response.status(Response.Status.CREATED).entity(service.creer(f)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(menu = "recouvrement", action = "MODIFIER")
    public Response modifier(@PathParam("id") Long id, RecFiche f) {
        f.setId(id);
        return Response.ok(service.modifier(f)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(menu = "recouvrement", action = "SUPPRIMER")
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }
}
