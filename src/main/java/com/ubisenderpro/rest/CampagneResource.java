package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.CampagneSenderAsync;
import com.ubisenderpro.service.CampagneService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/campaigns")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CampagneResource {

    @EJB
    private CampagneService campagneService;
    @EJB
    private CampagneSenderAsync campagneSenderAsync;

    @GET
    public List<Campagne> lister() { return campagneService.lister(); }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return campagneService.parId(id).map(c -> Response.ok(c).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(Campagne c) {
        return Response.status(Response.Status.CREATED).entity(campagneService.creer(c)).build();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, Campagne c) {
        c.setId(id);
        return Response.ok(campagneService.modifier(c)).build();
    }

    @POST
    @Path("/{id}/recipients")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response construire(@PathParam("id") Long id) {
        int total = campagneService.construireDestinataires(id);
        return Response.ok(Map.of("nbDestinataires", total)).build();
    }

    /**
     * Lance la campagne en arrière-plan : répond 202 immédiatement, l'envoi
     * progresse de façon asynchrone (statut EN_COURS -> TERMINEE).
     */
    @POST
    @Path("/{id}/launch")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response lancer(@PathParam("id") Long id) {
        campagneService.verifierLancable(id);
        campagneSenderAsync.lancer(id);
        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of("statut", "EN_COURS", "message", "Campagne lancée en arrière-plan")).build();
    }

    @POST
    @Path("/{id}/resume")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response reprendre(@PathParam("id") Long id) {
        campagneService.verifierLancable(id);
        campagneSenderAsync.lancer(id);
        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of("statut", "EN_COURS")).build();
    }

    @GET
    @Path("/{id}/statistics")
    public Response statistiques(@PathParam("id") Long id) {
        return campagneService.parId(id).map(c -> Response.ok(Map.of(
                "destinataires", c.getNbDestinataires(),
                "envoyes", c.getNbEnvoyes(),
                "distribues", c.getNbDistribues(),
                "lus", c.getNbLus(),
                "repondus", c.getNbRepondus(),
                "echoues", c.getNbEchoues(),
                "statut", c.getStatut())).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{id}/pause")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response suspendre(@PathParam("id") Long id) {
        campagneService.changerStatut(id, "SUSPENDUE");
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/cancel")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response annuler(@PathParam("id") Long id) {
        campagneService.changerStatut(id, "ANNULEE");
        return Response.ok().build();
    }
}
