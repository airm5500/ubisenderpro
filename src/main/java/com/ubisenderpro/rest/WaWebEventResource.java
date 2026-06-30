package com.ubisenderpro.rest;

import com.ubisenderpro.config.WaWebConfig;
import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.service.BotService;
import com.ubisenderpro.service.WaWebJournalService;
import com.ubisenderpro.service.WaWebSessionService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Réception des événements du service compagnon WhatsApp Web (Node) :
 * messages entrants et changements d'état de session. Non sécurisé par session
 * (appelé par le service Node), mais protégé par le token partagé X-Api-Token.
 */
@Path("/webhooks/wa-web")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WaWebEventResource {

    @EJB
    private WaWebJournalService journal;
    @EJB
    private WaWebSessionService sessionService;
    @EJB
    private BotService botService;

    @POST
    @Path("/message")
    public Response message(@HeaderParam("X-Api-Token") String token, Map<String, Object> body) {
        if (!tokenOk(token)) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
        Long sid = sessionId(str(body.get("sessionId")));
        String from = chiffres(str(body.get("from")));
        if (sid == null || from.isEmpty()) { return Response.status(Response.Status.BAD_REQUEST).build(); }
        String name = str(body.get("name"));
        String type = body.get("type") == null ? "TEXTE" : str(body.get("type"));
        String text = str(body.get("text"));
        String id = str(body.get("id"));
        Conversation conv = journal.enregistrerEntrant(sid, from, name, type, text, id);
        // Réponse automatique du bot (messages texte uniquement, hors doublons).
        if (conv != null && ("TEXTE".equalsIgnoreCase(type) || type == null) && !text.trim().isEmpty()) {
            botService.traiterEntrant(conv.getId(), text);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/status")
    public Response status(@HeaderParam("X-Api-Token") String token, Map<String, Object> body) {
        if (!tokenOk(token)) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
        Long sid = sessionId(str(body.get("sessionId")));
        String statut = str(body.get("status"));
        if (sid != null && !statut.isEmpty()) { sessionService.enregistrerStatut(sid, statut); }
        return Response.ok().build();
    }

    private boolean tokenOk(String token) {
        String attendu = WaWebConfig.token();
        return attendu == null || attendu.isEmpty() || attendu.equals(token);
    }

    private Long sessionId(String s) {
        String d = s == null ? "" : s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : Long.valueOf(d);
    }

    private String chiffres(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
