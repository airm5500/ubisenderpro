package com.ubisenderpro.rest;

import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.security.SessionStore;
import com.ubisenderpro.service.PermissionService;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menus et permissions (RBAC). Lecture des droits de l'utilisateur courant
 * ({@code /me}) pour piloter l'affichage ; gestion des permissions par rôle
 * réservée à l'ADMIN.
 */
@Path("/permissions")
@Secured
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PermissionResource {

    @EJB
    private PermissionService permissionService;

    @Inject
    private SessionStore sessionStore;

    /** Droits effectifs de l'utilisateur connecté : { menu: [actions] }. */
    @GET
    @Path("/me")
    public Map<String, Set<String>> mesPermissions(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        AuthenticatedUser u = utilisateur(authHeader);
        if (u == null) { return Collections.emptyMap(); }
        return permissionService.effectives(u.getRoles());
    }

    /** Catalogue des menus et de leurs actions (écran d'administration). */
    @GET
    @Path("/menus")
    @Secured(roles = {"ADMIN"})
    public List<Map<String, Object>> menus() {
        return permissionService.listerMenus();
    }

    /** Permissions accordées à un rôle, sous forme « menu:action ». */
    @GET
    @Path("/roles/{code}")
    @Secured(roles = {"ADMIN"})
    public List<String> permissionsRole(@PathParam("code") String code) {
        return permissionService.permissionsRole(code);
    }

    /** Remplace les permissions d'un rôle. */
    @PUT
    @Path("/roles/{code}")
    @Secured(roles = {"ADMIN"})
    public Response definir(@PathParam("code") String code, Map<String, List<String>> body) {
        List<String> perms = body == null ? null : body.get("permissions");
        permissionService.definirPermissionsRole(code, perms);
        return Response.noContent().build();
    }

    private AuthenticatedUser utilisateur(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) { return null; }
        return sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
    }
}
