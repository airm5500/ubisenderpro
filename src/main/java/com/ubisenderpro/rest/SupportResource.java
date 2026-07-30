package com.ubisenderpro.rest;

import com.ubisenderpro.entity.ApplicationEvent;
import com.ubisenderpro.entity.BotFaq;
import com.ubisenderpro.entity.SupportDemande;
import com.ubisenderpro.entity.SupportTicket;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.SupportEventService;
import com.ubisenderpro.service.SupportSanteService;
import com.ubisenderpro.service.SupportService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Centre de support. Accessible à tout utilisateur connecté pour « Me
 * contacter » et SES tickets ; les vues transverses (tous les tickets,
 * diagnostic, santé, historique) sont réservées aux rôles ADMIN / SUPPORT.
 */
@Path("/support")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupportResource {

    @EJB
    private SupportService supportService;
    @EJB
    private SupportEventService eventService;
    @EJB
    private SupportSanteService santeService;
    @Inject
    private SessionStore sessionStore;
    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /* ------------------------- Me contacter ------------------------- */

    @POST
    @Path("/demandes")
    public Response creerDemande(SupportDemande d,
                                 @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        if (u != null && d.getCreePar() == null) { d.setCreePar(u.getLogin()); }
        return Response.status(Response.Status.CREATED).entity(supportService.creerDemande(d)).build();
    }

    @GET
    @Path("/demandes")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public List<SupportDemande> listerDemandes(@QueryParam("limit") @DefaultValue("200") int limit) {
        return supportService.listerDemandes(limit);
    }

    /* ------------------------------ Tickets ------------------------------ */

    @POST
    @Path("/tickets")
    public Response creerTicket(SupportTicket t,
                                @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        return Response.status(Response.Status.CREATED)
                .entity(supportService.creerTicket(t, u == null ? null : u.getLogin())).build();
    }

    /** mine=true : mes tickets (tout utilisateur). Sinon : tous (ADMIN/SUPPORT). */
    @GET
    @Path("/tickets")
    public Response listerTickets(@QueryParam("mine") @DefaultValue("false") boolean mine,
                                  @QueryParam("statut") String statut,
                                  @QueryParam("q") String q,
                                  @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        if (mine) {
            return Response.ok(supportService.listerTickets(u == null ? "" : u.getLogin(), statut, q)).build();
        }
        if (!estSupport(u)) { return refus(); }
        return Response.ok(supportService.listerTickets(null, statut, q)).build();
    }

    @GET
    @Path("/tickets/{id}")
    public Response ticket(@PathParam("id") Long id,
                           @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        SupportTicket t = supportService.ticket(id);
        if (t == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        AuthenticatedUser u = utilisateur(auth);
        if (!peutVoir(u, t)) { return refus(); }
        return Response.ok(t).build();
    }

    @GET
    @Path("/tickets/{id}/messages")
    public Response messages(@PathParam("id") Long id,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        SupportTicket t = supportService.ticket(id);
        if (t == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        AuthenticatedUser u = utilisateur(auth);
        if (!peutVoir(u, t)) { return refus(); }
        return Response.ok(supportService.messages(id)).build();
    }

    @POST
    @Path("/tickets/{id}/messages")
    public Response repondre(@PathParam("id") Long id, Map<String, String> body,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        SupportTicket t = supportService.ticket(id);
        if (t == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        AuthenticatedUser u = utilisateur(auth);
        if (!peutVoir(u, t)) { return refus(); }
        String direction = estSupport(u) && !estAuteur(u, t) ? "INTERNE" : "CLIENT";
        return Response.ok(supportService.ajouterMessage(id, direction,
                u == null ? null : u.getLogin(),
                body == null ? null : body.get("corps"),
                body == null ? null : body.get("pieces"))).build();
    }

    @PUT
    @Path("/tickets/{id}/statut")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Response changerStatut(@PathParam("id") Long id, Map<String, String> body,
                                  @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        return Response.ok(supportService.changerStatut(id,
                body == null ? null : body.get("statut"),
                u == null ? null : u.getLogin())).build();
    }

    @PUT
    @Path("/tickets/{id}/affecter")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Response affecter(@PathParam("id") Long id, Map<String, String> body,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        return Response.ok(supportService.affecter(id,
                body == null ? null : body.get("affecteA"),
                u == null ? null : u.getLogin())).build();
    }

    /* --------------------- Journal d'événements (bugs) --------------------- */

    /** Point de collecte unique (erreurs JS notamment). Ouvert à tout connecté, throttlé. */
    @POST
    @Path("/events")
    public Response collecter(Map<String, String> body,
                              @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        if (body == null) { return Response.ok(Map.of("collecte", false)).build(); }
        ApplicationEvent e = eventService.collecter(
                body.getOrDefault("type", "JS"),
                body.get("module"),
                body.getOrDefault("niveau", "ERROR"),
                body.get("message"),
                body.get("payload"),
                u == null ? null : u.getLogin(),
                body.get("url"));
        return Response.ok(Map.of("collecte", e != null)).build();
    }

    @GET
    @Path("/events")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public List<ApplicationEvent> listerEvents(@QueryParam("niveau") String niveau,
                                               @QueryParam("q") String q,
                                               @QueryParam("limit") @DefaultValue("300") int limit) {
        return eventService.lister(niveau, q, limit);
    }

    /** Crée un ticket depuis un événement capturé et les lie. */
    @POST
    @Path("/events/{id}/ticket")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Response ticketDepuisEvent(@PathParam("id") Long id,
                                      @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        ApplicationEvent e = eventService.parId(id);
        if (e == null) { return Response.status(Response.Status.NOT_FOUND).build(); }
        AuthenticatedUser u = utilisateur(auth);
        SupportTicket t = new SupportTicket();
        t.setType("BUG");
        t.setPriorite("ERROR".equals(e.getNiveau()) || "FATAL".equals(e.getNiveau()) ? "HAUTE" : "NORMALE");
        t.setModule(e.getModule());
        t.setSujet("[" + e.getType() + "] " + (e.getMessageCourt() == null ? "Événement #" + id
                : e.getMessageCourt().substring(0, Math.min(180, e.getMessageCourt().length()))));
        t.setDescription("Événement capturé #" + id + " (x" + e.getOccurrences() + ")\n"
                + "Signature : " + e.getSignature() + "\n\n" + n(e.getPayloadJson()));
        t.setEventSignature(e.getSignature());
        SupportTicket cree = supportService.creerTicket(t, u == null ? null : u.getLogin());
        eventService.lierTicket(id, cree.getId());
        return Response.status(Response.Status.CREATED).entity(cree).build();
    }

    @DELETE
    @Path("/events/purge")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Response purger() {
        return Response.ok(Map.of("purges", eventService.purger())).build();
    }

    /* ------------------------------- Santé ------------------------------- */

    @GET
    @Path("/sante")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Map<String, Object> sante() { return santeService.sante(); }

    /* ---------------------- FAQ (mutualisée avec le bot) ---------------------- */

    /** FAQ active, en lecture pour tout utilisateur connecté (gérée dans Paramètres > Bot). */
    @GET
    @Path("/faq")
    public List<BotFaq> faq() {
        return em.createQuery("SELECT f FROM BotFaq f WHERE f.actif = true ORDER BY f.ordre, f.id", BotFaq.class)
                .setMaxResults(500).getResultList();
    }

    /* ------------------------------ interne ------------------------------ */

    private AuthenticatedUser utilisateur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        return sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
    }

    private static boolean estSupport(AuthenticatedUser u) {
        return u != null && (u.hasRole("ADMIN") || u.hasRole("SUPPORT"));
    }

    private static boolean estAuteur(AuthenticatedUser u, SupportTicket t) {
        return u != null && t.getUtilisateur() != null && t.getUtilisateur().equals(u.getLogin());
    }

    /** Le propriétaire du ticket ou l'équipe support. */
    private static boolean peutVoir(AuthenticatedUser u, SupportTicket t) {
        return estSupport(u) || estAuteur(u, t);
    }

    private static Response refus() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("erreur", "Accès réservé à l'équipe support.")).build();
    }

    private static String n(String s) { return s == null ? "" : s; }
}
