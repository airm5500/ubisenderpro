package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.WhatsappAccount;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.WhatsappService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/whatsapp")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WhatsappResource {

    @EJB
    private WhatsappService whatsappService;

    @GET
    @Path("/accounts")
    public List<WhatsappAccount> comptes() { return whatsappService.listerComptes(); }

    @POST
    @Path("/accounts")
    @Secured(roles = {"ADMIN"})
    public Response creerCompte(WhatsappAccount a) {
        return Response.status(Response.Status.CREATED).entity(whatsappService.creerCompte(a)).build();
    }

    @PUT
    @Path("/accounts/{id}")
    @Secured(roles = {"ADMIN"})
    public Response modifierCompte(@PathParam("id") Long id, WhatsappAccount a) {
        a.setId(id);
        return Response.ok(whatsappService.modifierCompte(a)).build();
    }

    @POST
    @Path("/messages/text")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    public Response envoyerTexte(Map<String, Object> body) {
        Long accountId = Long.valueOf(String.valueOf(body.get("accountId")));
        String numero = String.valueOf(body.get("numero"));
        String texte = String.valueOf(body.get("texte"));
        Message m = whatsappService.envoyerTexte(accountId, numero, texte, null);
        return Response.ok(m).build();
    }

    @POST
    @Path("/messages/media")
    @Secured(roles = {"ADMIN", "MARKETING", "SUPERVISEUR", "AGENT"})
    public Response envoyerMedia(Map<String, Object> body) {
        Long accountId = Long.valueOf(String.valueOf(body.get("accountId")));
        String numero = String.valueOf(body.get("numero"));
        String type = String.valueOf(body.getOrDefault("type", "image"));
        String url = String.valueOf(body.get("url"));
        String legende = body.get("legende") == null ? null : String.valueOf(body.get("legende"));
        Message m = whatsappService.envoyerMedia(accountId, numero, type, url, legende, null);
        return Response.ok(m).build();
    }
}
