package com.ubisenderpro.rest;

import com.ubisenderpro.dto.UserRequest;
import com.ubisenderpro.entity.Role;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ConnexionLogService;
import com.ubisenderpro.service.JournalService;
import com.ubisenderpro.service.UserService;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Gestion des utilisateurs applicatifs — réservée aux administrateurs.
 */
@Path("/users")
@Secured(roles = {"ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @EJB
    private UserService userService;
    @EJB
    private ConnexionLogService connexionLogService;
    @EJB
    private JournalService journalService;

    @GET
    public List<Map<String, Object>> lister() {
        return userService.lister();
    }

    @GET
    @Path("/roles")
    public List<Role> roles() {
        return userService.listerRoles();
    }

    @POST
    public Response creer(UserRequest req) {
        if (req.getLogin() == null || req.getLogin().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erreur", "Login obligatoire")).build();
        }
        if (userService.loginExiste(req.getLogin(), null)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("erreur", "Ce login existe déjà")).build();
        }
        return Response.status(Response.Status.CREATED).entity(userService.creer(req)).build();
    }

    @PUT
    @Path("/{id}")
    public Response modifier(@PathParam("id") Long id, UserRequest req) {
        Map<String, Object> u = userService.modifier(id, req);
        return u == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(u).build();
    }

    @POST
    @Path("/{id}/activate")
    public Response activer(@PathParam("id") Long id) {
        userService.definirActif(id, true);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/deactivate")
    public Response desactiver(@PathParam("id") Long id) {
        userService.definirActif(id, false);
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/reset-password")
    public Response reinitialiser(@PathParam("id") Long id, Map<String, Object> body) {
        String nouveau = body == null ? null : (String) body.get("motDePasse");
        String applique = userService.reinitialiserMotDePasse(id, nouveau);
        return applique == null ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.ok(Map.of("motDePasse", applique)).build();
    }

    @GET
    @Path("/connexions")
    public List<com.ubisenderpro.entity.ConnexionLog> connexions(@QueryParam("limit") Integer limit) {
        return connexionLogService.lister(limit == null ? 200 : limit);
    }

    @GET
    @Path("/journal")
    public List<com.ubisenderpro.entity.JournalAction> journal(@QueryParam("limit") Integer limit) {
        return journalService.lister(limit == null ? 200 : limit);
    }
}
