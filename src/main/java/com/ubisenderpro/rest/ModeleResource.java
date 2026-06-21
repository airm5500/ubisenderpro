package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.AuditService;
import com.ubisenderpro.service.ModeleService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/templates")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModeleResource {

    @EJB
    private ModeleService modeleService;
    @EJB
    private AuditService auditService;

    @GET
    public List<ModeleMessage> lister() { return modeleService.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return modeleService.parId(id).map(m -> Response.ok(m).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(ModeleMessage m, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        ModeleMessage cree = modeleService.creer(m);
        auditService.tracer(auth, "CREATION", "Modele", cree.getId(), cree.getNom());
        return Response.status(Response.Status.CREATED).entity(cree).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, ModeleMessage m,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        m.setId(id);
        ModeleMessage mod = modeleService.modifier(m);
        auditService.tracer(auth, "MODIFICATION", "Modele", id, mod.getNom());
        return Response.ok(mod).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response supprimer(@PathParam("id") Long id,
                              @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        modeleService.supprimer(id);
        auditService.tracer(auth, "SUPPRESSION", "Modele", id, null);
        return Response.noContent().build();
    }
}
