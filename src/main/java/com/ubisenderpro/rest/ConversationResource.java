package com.ubisenderpro.rest;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ConversationService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/conversations")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationResource {

    @EJB
    private ConversationService conversationService;

    @GET
    public PageResult<Conversation> lister(@QueryParam("statut") String statut,
                                           @QueryParam("agentId") Long agentId,
                                           @QueryParam("start") @DefaultValue("0") int start,
                                           @QueryParam("limit") @DefaultValue("25") int limit) {
        return conversationService.lister(statut, agentId, start, limit);
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return conversationService.parId(id).map(c -> Response.ok(c).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/messages")
    public List<Message> messages(@PathParam("id") Long id) {
        return conversationService.messages(id);
    }

    @POST
    @Path("/{id}/assign")
    public Response affecter(@PathParam("id") Long id, Map<String, Object> body) {
        Long agentId = body.get("agentId") == null ? null : Long.valueOf(String.valueOf(body.get("agentId")));
        conversationService.affecter(id, agentId);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/close")
    public Response fermer(@PathParam("id") Long id) {
        conversationService.changerStatut(id, "CLOTUREE");
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/reopen")
    public Response rouvrir(@PathParam("id") Long id) {
        conversationService.changerStatut(id, "OUVERTE");
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/read")
    public Response marquerLu(@PathParam("id") Long id) {
        conversationService.marquerLu(id);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/notes")
    public Response ajouterNote(@PathParam("id") Long id, Map<String, Object> body) {
        String note = String.valueOf(body.get("note"));
        return Response.ok(conversationService.ajouterNote(id, note, null)).build();
    }
}
