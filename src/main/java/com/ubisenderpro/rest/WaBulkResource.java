package com.ubisenderpro.rest;

import com.ubisenderpro.dto.WaBulkRequest;
import com.ubisenderpro.entity.WaBulkJob;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.WaBulkService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi en masse via WhatsApp Web (5 variantes, pièce jointe, débit).
 */
@Path("/wa-bulk")
@Secured(roles = {"ADMIN", "MARKETING"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WaBulkResource {

    @EJB
    private WaBulkService service;

    @GET
    public List<WaBulkJob> lister() { return service.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return service.parId(id).map(j -> Response.ok(j).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/destinataires")
    public java.util.List<com.ubisenderpro.entity.WaBulkDestinataire> destinataires(@PathParam("id") Long id) {
        return service.destinataires(id);
    }

    @POST
    public Response creer(WaBulkRequest req) {
        try {
            WaBulkJob j = service.creer(req);
            return Response.status(Response.Status.CREATED).entity(j).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(erreur(e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/launch")
    public Response lancer(@PathParam("id") Long id) {
        try {
            return Response.ok(service.lancer(id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(erreur(e.getMessage())).build();
        }
    }

    private Map<String, Object> erreur(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("erreur", message);
        return m;
    }
}
