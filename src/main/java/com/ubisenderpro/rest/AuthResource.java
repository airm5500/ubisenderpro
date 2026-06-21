package com.ubisenderpro.rest;

import com.ubisenderpro.dto.LoginRequest;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.AuthService;
import com.ubisenderpro.service.ConnexionLogService;
import com.ubisenderpro.service.GeoIpService;
import com.ubisenderpro.service.JournalService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @EJB
    private AuthService authService;
    @EJB
    private JournalService journalService;
    @EJB
    private ConnexionLogService connexionLogService;
    @EJB
    private GeoIpService geoIpService;
    @Inject
    private SessionStore sessionStore;

    @POST
    @Path("/login")
    public Response login(LoginRequest req, @Context HttpServletRequest request) {
        Optional<AuthenticatedUser> user = authService.authentifier(req.getLogin(), req.getMotDePasse());
        if (!user.isPresent()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("erreur", "Identifiants invalides")).build();
        }
        AuthenticatedUser u = user.get();
        String token = sessionStore.create(u);
        String ip = ip(request);
        journalService.tracer(u.getId(), u.getLogin(), "CONNEXION", "Utilisateur", u.getId(), null, ip);
        Long logId = connexionLogService.ouvrir(u.getId(), u.getLogin(), token, ip, poste(request), null);
        geoIpService.localiser(logId, ip);

        Map<String, Object> reponse = new HashMap<>();
        reponse.put("token", token);
        reponse.put("user", infoUtilisateur(u));
        return Response.ok(reponse).build();
    }

    @POST
    @Path("/logout")
    @Secured
    public Response logout(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = extraireToken(authHeader);
        connexionLogService.fermer(token);
        sessionStore.invalidate(token);
        return Response.ok(Map.of("message", "Déconnecté")).build();
    }

    /** IP cliente, en tenant compte d'un éventuel reverse-proxy (X-Forwarded-For). */
    private String ip(HttpServletRequest r) {
        if (r == null) { return null; }
        String xff = r.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) { return xff.split(",")[0].trim(); }
        return r.getRemoteAddr();
    }

    /** Nom du poste (résolution inverse de l'IP si le conteneur la fournit). */
    private String poste(HttpServletRequest r) {
        if (r == null) { return null; }
        String h = r.getRemoteHost();
        return (h != null && !h.equals(r.getRemoteAddr())) ? h : null;
    }

    @GET
    @Path("/me")
    @Secured
    public Response me(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        AuthenticatedUser u = sessionStore.validate(extraireToken(authHeader));
        if (u == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return Response.ok(infoUtilisateur(u)).build();
    }

    private Map<String, Object> infoUtilisateur(AuthenticatedUser u) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", u.getId());
        info.put("login", u.getLogin());
        info.put("nomComplet", u.getNomComplet());
        info.put("roles", u.getRoles());
        return info;
    }

    private String extraireToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring("Bearer ".length()).trim();
    }
}
