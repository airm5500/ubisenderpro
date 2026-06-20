package com.ubisenderpro.rest;

import com.ubisenderpro.dto.CommandeRequest;
import com.ubisenderpro.entity.Commande;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.CommandeService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/orders")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommandeResource {

    @EJB
    private CommandeService commandeService;

    @GET
    public List<Commande> lister(@QueryParam("statut") String statut,
                                 @QueryParam("start") @DefaultValue("0") int start,
                                 @QueryParam("limit") @DefaultValue("25") int limit) {
        return commandeService.lister(statut, start, limit);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return commandeService.parId(id).map(c -> Response.ok(Map.of(
                "commande", c, "lignes", commandeService.details(id))).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "SUPERVISEUR", "AGENT"})
    public Response creer(CommandeRequest req) {
        Commande c = commandeService.creer(req.getCommande(), req.getLignes());
        return Response.status(Response.Status.CREATED).entity(c).build();
    }

    @POST
    @Path("/{id}/confirm")
    public Response confirmer(@PathParam("id") Long id) { return statut(id, "CONFIRMEE"); }

    @POST
    @Path("/{id}/prepare")
    public Response preparer(@PathParam("id") Long id) { return statut(id, "EN_PREPARATION"); }

    @POST
    @Path("/{id}/ready")
    public Response prete(@PathParam("id") Long id) { return statut(id, "PRETE"); }

    @POST
    @Path("/{id}/deliver")
    public Response livrer(@PathParam("id") Long id) { return statut(id, "LIVREE"); }

    @POST
    @Path("/{id}/cancel")
    public Response annuler(@PathParam("id") Long id) { return statut(id, "ANNULEE"); }

    private Response statut(Long id, String statut) {
        Commande c = commandeService.changerStatut(id, statut);
        return c == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(c).build();
    }
}
