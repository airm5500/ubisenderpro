package com.ubisenderpro.rest;

import com.ubisenderpro.entity.LicenceEvenement;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.LicenceService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Gestion de la licence. L'état est lisible par tout utilisateur connecté
 * (bandeau d'alerte) ; l'import/demande/journal sont réservés à ADMIN/SUPPORT —
 * le rôle SUPPORT reste le « mode maintenance » même licence expirée.
 */
@Path("/licence")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LicenceResource {

    @EJB
    private LicenceService licenceService;
    @Inject
    private SessionStore sessionStore;

    /** État consolidé (statut, échéance, modules, alertes) — tout utilisateur. */
    @GET
    @Path("/etat")
    public Map<String, Object> etat() {
        licenceService.battement(); // anti-recul d'horloge : mémorise la date vue
        return licenceService.etat();
    }

    /** Importe une clé d'activation ou le contenu d'un fichier .lic. */
    @POST
    @Path("/importer")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Response importer(Map<String, String> body,
                             @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        licenceService.importer(body == null ? null : body.get("contenu"),
                u == null ? null : u.getLogin());
        return Response.ok(licenceService.etat()).build();
    }

    /** Génère la demande d'activation hors ligne (.licreq) à transmettre à l'éditeur. */
    @POST
    @Path("/demande")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public Map<String, Object> demande(Map<String, String> body,
                                       @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        AuthenticatedUser u = utilisateur(auth);
        return licenceService.genererDemande(
                body == null ? null : body.get("societe"),
                body == null ? null : body.get("email"),
                u == null ? null : u.getLogin());
    }

    /** Journal local des événements de licence. */
    @GET
    @Path("/evenements")
    @Secured(roles = {"ADMIN", "SUPPORT"})
    public List<LicenceEvenement> evenements(@QueryParam("limit") @DefaultValue("100") int limit) {
        return licenceService.evenements(limit);
    }

    private AuthenticatedUser utilisateur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        return sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
    }
}
