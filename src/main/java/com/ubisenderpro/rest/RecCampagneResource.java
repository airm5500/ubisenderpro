package com.ubisenderpro.rest;

import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.RecCampagneService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Campagnes de relance ciblées (aperçu + envoi groupé).
 */
@Path("/recouvrement/campagnes")
@Secured(menu = "recouvrement", action = "VOIR")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecCampagneResource {

    @EJB
    private RecCampagneService service;
    @EJB
    private com.ubisenderpro.service.RecScopeService scope;
    @Inject
    private SessionStore sessionStore;

    @POST
    @Path("/preview")
    public Response preview(Map<String, Object> body, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String agence = portee(authHeader, str(body, "agence"));
        List<Map<String, Object>> cibles = service.cibler(
                agence, str(body, "responsable"), str(body, "segment"), str(body, "profil"),
                montant(body, "montantMin"), entier(body, "joursMin"));
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("count", cibles.size());
        r.put("cibles", cibles.size() > 50 ? cibles.subList(0, 50) : cibles);
        return Response.ok(r).build();
    }

    @POST
    @Path("/envoyer")
    @Secured(menu = "recouvrement", action = "ENVOYER")
    public Response envoyer(Map<String, Object> body, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long modeleId = entierLong(body, "modeleId");
        if (modeleId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("erreur", "Choisissez un modèle de relance.")).build();
        }
        AuthenticatedUser u = utilisateur(authHeader);
        String agence = portee(authHeader, str(body, "agence"));
        Map<String, Object> r = service.envoyer(
                agence, str(body, "responsable"), str(body, "segment"), str(body, "profil"),
                montant(body, "montantMin"), entier(body, "joursMin"), modeleId, str(body, "canal"),
                u == null ? null : u.getId(), u == null ? null : u.getLogin());
        return Response.ok(r).build();
    }

    private AuthenticatedUser utilisateur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        return sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
    }

    /** Force l'agence de l'utilisateur si cloisonné, sinon garde l'agence demandée. */
    private String portee(String authHeader, String demandee) {
        String p = scope.agencePortee(utilisateur(authHeader));
        return p != null ? p : demandee;
    }

    private String str(Map<String, Object> b, String k) {
        Object v = b == null ? null : b.get(k);
        return v == null || String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim();
    }
    private Integer entier(Map<String, Object> b, String k) {
        String s = str(b, k);
        try { return s == null ? null : Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }
    private Long entierLong(Map<String, Object> b, String k) {
        String s = str(b, k);
        try { return s == null ? null : Long.valueOf(s); } catch (NumberFormatException e) { return null; }
    }
    private BigDecimal montant(Map<String, Object> b, String k) {
        String s = str(b, k);
        try { return s == null ? null : new BigDecimal(s.replace(",", ".")); } catch (NumberFormatException e) { return null; }
    }
}
