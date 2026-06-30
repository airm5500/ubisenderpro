package com.ubisenderpro.rest;

import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecPaiement;
import com.ubisenderpro.entity.RecPromesse;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.RecCreanceService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Mouvements financiers d'un client : créances (factures/avoirs), règlements
 * et promesses de paiement.
 */
@Path("/recouvrement/clients/{clientId}")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecMouvementResource {

    @EJB
    private RecCreanceService service;

    /* -------- Créances -------- */

    @GET
    @Path("/creances")
    public List<RecCreance> creances(@PathParam("clientId") Long clientId) {
        return service.listerCreances(clientId);
    }

    @POST
    @Path("/creances")
    @Secured(menu = "recouvrement", action = "CREER")
    public Response creerCreance(@PathParam("clientId") Long clientId, RecCreance c) {
        c.setClientId(clientId);
        return Response.status(Response.Status.CREATED).entity(service.creerCreance(c)).build();
    }

    @PUT
    @Path("/creances/{id}")
    @Secured(menu = "recouvrement", action = "MODIFIER")
    public Response modifierCreance(@PathParam("clientId") Long clientId, @PathParam("id") Long id, RecCreance c) {
        c.setClientId(clientId);
        return Response.ok(service.modifierCreance(id, c)).build();
    }

    @DELETE
    @Path("/creances/{id}")
    @Secured(menu = "recouvrement", action = "SUPPRIMER")
    public Response supprimerCreance(@PathParam("id") Long id) {
        service.supprimerCreance(id);
        return Response.noContent().build();
    }

    /* -------- Règlements -------- */

    @GET
    @Path("/paiements")
    public List<RecPaiement> paiements(@PathParam("clientId") Long clientId) {
        return service.listerPaiements(clientId);
    }

    @POST
    @Path("/paiements")
    @Secured(menu = "recouvrement", action = "CREER")
    public Response creerPaiement(@PathParam("clientId") Long clientId, RecPaiement p) {
        p.setClientId(clientId);
        return Response.status(Response.Status.CREATED).entity(service.creerPaiement(p)).build();
    }

    @DELETE
    @Path("/paiements/{id}")
    @Secured(menu = "recouvrement", action = "SUPPRIMER")
    public Response supprimerPaiement(@PathParam("id") Long id) {
        service.supprimerPaiement(id);
        return Response.noContent().build();
    }

    /* -------- Promesses -------- */

    @GET
    @Path("/promesses")
    public List<RecPromesse> promesses(@PathParam("clientId") Long clientId) {
        return service.listerPromesses(clientId);
    }

    @POST
    @Path("/promesses")
    @Secured(menu = "recouvrement", action = "CREER")
    public Response creerPromesse(@PathParam("clientId") Long clientId, RecPromesse p) {
        p.setClientId(clientId);
        return Response.status(Response.Status.CREATED).entity(service.creerPromesse(p)).build();
    }

    @PUT
    @Path("/promesses/{id}")
    @Secured(menu = "recouvrement", action = "MODIFIER")
    public Response modifierPromesse(@PathParam("id") Long id, RecPromesse p) {
        return Response.ok(service.modifierPromesse(id, p)).build();
    }

    @DELETE
    @Path("/promesses/{id}")
    @Secured(menu = "recouvrement", action = "SUPPRIMER")
    public Response supprimerPromesse(@PathParam("id") Long id) {
        service.supprimerPromesse(id);
        return Response.noContent().build();
    }
}
