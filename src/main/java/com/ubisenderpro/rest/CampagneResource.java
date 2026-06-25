package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.CampagneSenderAsync;
import com.ubisenderpro.service.CampagneService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
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
    @EJB
    private com.ubisenderpro.service.AuditService auditService;
    @Inject
    private SessionStore sessionStore;

    @GET
    public List<Campagne> lister(@QueryParam("categorie") String categorie) {
        return campagneService.lister(categorie);
    }

    /** Tableau de performance (§18) : totaux + lignes, filtrés période/canal/catégorie. */
    @GET
    @Path("/performance")
    public Response performance(@QueryParam("du") String du, @QueryParam("au") String au,
                                @QueryParam("canal") String canal, @QueryParam("categorie") String categorie) {
        java.time.LocalDate dDu = parseDate(du);
        java.time.LocalDate dAu = parseDate(au);
        return Response.ok(campagneService.performance(dDu, dAu, canal, categorie)).build();
    }

    private static java.time.LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) { return null; }
        try { return java.time.LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
    }

    @GET
    @Path("/{id}")
    public Response parId(@PathParam("id") Long id) {
        return campagneService.parId(id).map(c -> Response.ok(c).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response creer(Campagne c, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        c.setCreePar(utilisateurId(authHeader));
        Campagne cree = campagneService.creer(c);
        auditService.tracer(authHeader, "CREATION", "Campagne", cree.getId(), cree.getNom());
        return Response.status(Response.Status.CREATED).entity(cree).build();
    }

    private Long utilisateurId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        AuthenticatedUser u = sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
        return u == null ? null : u.getId();
    }

    @PUT
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response modifier(@PathParam("id") Long id, Campagne c) {
        c.setId(id);
        return Response.ok(campagneService.modifier(c)).build();
    }

    @DELETE
    @Path("/{id}")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response supprimer(@PathParam("id") Long id, @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        campagneService.supprimer(id);
        auditService.tracer(auth, "SUPPRESSION", "Campagne", id, null);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/recipients")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response construire(@PathParam("id") Long id) {
        int total = campagneService.construireDestinataires(id);
        return Response.ok(Map.of("nbDestinataires", total)).build();
    }

    /** Détails d'une campagne : liste des destinataires avec leur statut d'envoi. */
    @GET
    @Path("/{id}/recipients")
    public List<com.ubisenderpro.entity.CampagneDestinataire> destinataires(@PathParam("id") Long id) {
        return campagneService.destinataires(id);
    }

    /**
     * Relance les envois en échec d'une campagne : les destinataires ECHOUE
     * repassent EN_ATTENTE (l'historique des tentatives est conservé) et la
     * campagne est relancée en arrière-plan.
     */
    @POST
    @Path("/{id}/relancer")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response relancer(@PathParam("id") Long id, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        int reinities = campagneService.reinitialiserEchecs(id);
        if (reinities == 0) {
            return Response.ok(Map.of("relances", 0, "message", "Aucun envoi en échec à relancer")).build();
        }
        campagneService.verifierLancable(id);
        campagneSenderAsync.lancer(id);
        auditService.tracer(authHeader, "RELANCE", "Campagne", id, reinities + " envoi(s) en échec relancé(s)");
        return Response.status(Response.Status.ACCEPTED)
                .entity(Map.of("relances", reinities, "statut", "EN_COURS")).build();
    }

    /**
     * Lance la campagne en arrière-plan : répond 202 immédiatement, l'envoi
     * progresse de façon asynchrone (statut EN_COURS -> TERMINEE).
     */
    @POST
    @Path("/{id}/launch")
    @Secured(roles = {"ADMIN", "MARKETING"})
    public Response lancer(@PathParam("id") Long id, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        campagneService.verifierLancable(id);
        campagneSenderAsync.lancer(id);
        auditService.tracer(authHeader, "LANCEMENT", "Campagne", id, null);
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
