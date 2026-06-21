package com.ubisenderpro.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.ubisenderpro.entity.WaWarmup;
import com.ubisenderpro.entity.WaWebSession;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.WaWarmupService;
import com.ubisenderpro.service.WaWebSessionService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Canal WhatsApp Web (non officiel) : gestion des sessions et connexion par QR.
 */
@Path("/wa-web")
@Secured(roles = {"ADMIN", "MARKETING"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WaWebResource {

    @EJB
    private WaWebSessionService service;
    @EJB
    private WaWarmupService warmupService;
    @Inject
    private SessionStore sessionStore;

    @GET
    @Path("/sessions")
    public List<WaWebSession> lister() { return service.lister(); }

    @POST
    @Path("/sessions")
    public Response creer(WaWebSession s) {
        return Response.status(Response.Status.CREATED).entity(service.creer(s)).build();
    }

    @PUT
    @Path("/sessions/{id}")
    public Response modifier(@PathParam("id") Long id, WaWebSession s) {
        s.setId(id);
        return Response.ok(service.modifier(s)).build();
    }

    @DELETE
    @Path("/sessions/{id}")
    public Response supprimer(@PathParam("id") Long id) {
        service.supprimer(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/sessions/{id}/start")
    public Response demarrer(@PathParam("id") Long id) {
        JsonNode etat = service.demarrer(id);
        return Response.ok(etat.toString()).build();
    }

    @GET
    @Path("/sessions/{id}/status")
    public Response statut(@PathParam("id") Long id) {
        return Response.ok(service.statut(id).toString()).build();
    }

    @POST
    @Path("/sessions/{id}/logout")
    public Response deconnecter(@PathParam("id") Long id) {
        service.deconnecter(id);
        return Response.noContent().build();
    }

    // ----- Filtre de numéros (Phase 3) -----
    @POST
    @Path("/sessions/{id}/check-numbers")
    public Response verifier(@PathParam("id") Long id, Map<String, Object> body) {
        List<String> numeros = new ArrayList<>();
        Object src = body == null ? null : (body.containsKey("numeros") ? body.get("numeros") : body.get("numbers"));
        if (src instanceof List) {
            for (Object o : (List<?>) src) { if (o != null) numeros.add(String.valueOf(o)); }
        }
        return Response.ok(service.verifierNumeros(id, numeros).toString()).build();
    }

    // ----- Envoi unitaire (composeur dual-canal) -----
    @POST
    @Path("/sessions/{id}/send")
    public Response envoyerTexte(@PathParam("id") Long id, Map<String, Object> body,
                                 @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String numero = body == null ? null : String.valueOf(body.get("numero"));
        String texte = body == null ? "" : String.valueOf(body.getOrDefault("texte", ""));
        return Response.ok(service.envoyerTexte(id, numero, texte, utilisateurId(authHeader))).build();
    }

    @POST
    @Path("/sessions/{id}/send-media")
    public Response envoyerMedia(@PathParam("id") Long id, Map<String, Object> body,
                                 @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String numero = body == null ? null : String.valueOf(body.get("numero"));
        String type = body != null && body.get("type") != null ? String.valueOf(body.get("type")) : "image";
        String url = body != null && body.get("mediaUrl") != null ? String.valueOf(body.get("mediaUrl")) : null;
        String caption = body != null && body.get("caption") != null ? String.valueOf(body.get("caption")) : null;
        String mime = body != null && body.get("mimeType") != null ? String.valueOf(body.get("mimeType")) : null;
        String nom = body != null && body.get("fileName") != null ? String.valueOf(body.get("fileName")) : null;
        return Response.ok(service.envoyerMedia(id, numero, type, url, caption, mime, nom,
                utilisateurId(authHeader))).build();
    }

    private Long utilisateurId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        AuthenticatedUser u = sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
        return u == null ? null : u.getId();
    }

    // ----- Réchauffeur (warming) -----
    @GET
    @Path("/sessions/{id}/warmup")
    public Response warmup(@PathParam("id") Long id) {
        WaWarmup w = warmupService.config(id);
        return w == null ? Response.noContent().build() : Response.ok(w).build();
    }

    @PUT
    @Path("/sessions/{id}/warmup")
    public Response sauverWarmup(@PathParam("id") Long id, WaWarmup data) {
        return Response.ok(warmupService.enregistrer(id, data)).build();
    }

    // ----- Extraction (Phase 4) -----
    @GET
    @Path("/sessions/{id}/contacts")
    public Response contacts(@PathParam("id") Long id) {
        return Response.ok(service.contacts(id).toString()).build();
    }

    @GET
    @Path("/sessions/{id}/groups")
    public Response groupes(@PathParam("id") Long id) {
        return Response.ok(service.groupes(id).toString()).build();
    }

    @GET
    @Path("/sessions/{id}/participants")
    public Response participants(@PathParam("id") Long id, @QueryParam("jid") String jid) {
        return Response.ok(service.participants(id, jid).toString()).build();
    }
}
