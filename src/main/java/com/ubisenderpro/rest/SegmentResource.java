package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.Segment;
import com.ubisenderpro.entity.SegmentFiltre;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.SegmentService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/segments")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SegmentResource {

    @EJB
    private SegmentService segmentService;

    @GET
    public List<Segment> lister() { return segmentService.lister(); }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(Segment s) {
        return Response.status(Response.Status.CREATED).entity(segmentService.creer(s)).build();
    }

    @POST
    @Path("/{id}/filtres")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response ajouterFiltre(@PathParam("id") Long id, SegmentFiltre f) {
        f.setSegmentId(id);
        return Response.status(Response.Status.CREATED).entity(segmentService.ajouterFiltre(f)).build();
    }

    @GET
    @Path("/{id}/filtres")
    public List<SegmentFiltre> filtres(@PathParam("id") Long id) { return segmentService.filtres(id); }

    @POST
    @Path("/{id}/preview")
    public Map<String, Object> previsualiser(@PathParam("id") Long id) {
        List<ClientContact> contacts = segmentService.evaluer(id);
        return Map.of("total", contacts.size(), "contacts", contacts);
    }
}
