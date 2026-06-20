package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Opportunite;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.OpportuniteService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/opportunities")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpportuniteResource {

    @EJB
    private OpportuniteService opportuniteService;

    @GET
    public List<Opportunite> lister(@QueryParam("statut") String statut,
                                    @QueryParam("agentId") Long agentId) {
        return opportuniteService.lister(statut, agentId);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return opportuniteService.parId(id).map(o -> Response.ok(o).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    public Response creer(Opportunite o) {
        return Response.status(Response.Status.CREATED).entity(opportuniteService.creer(o)).build();
    }

    @PUT
    @Path("/{id}")
    public Response modifier(@PathParam("id") Long id, Opportunite o) {
        o.setId(id);
        return Response.ok(opportuniteService.modifier(o)).build();
    }

    @POST
    @Path("/{id}/status")
    public Response changerStatut(@PathParam("id") Long id, Map<String, Object> body) {
        String statut = String.valueOf(body.get("statut"));
        Opportunite o = opportuniteService.changerStatut(id, statut);
        return o == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(o).build();
    }
}
