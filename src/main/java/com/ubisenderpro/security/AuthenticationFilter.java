package com.ubisenderpro.security;

import com.ubisenderpro.service.PermissionService;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;

/**
 * Valide le jeton de session porté par l'en-tête Authorization: Bearer <token>
 * et applique les restrictions de rôle déclarées via @Secured.
 * Capture aussi l'IP et le nom de poste de l'appelant (RequestContext) pour la
 * journalisation, et nettoie ce contexte en fin de requête.
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Context
    private ResourceInfo resourceInfo;
    @Context
    private HttpServletRequest servletRequest;

    @Inject
    private SessionStore sessionStore;

    @Inject
    private PermissionService permissionService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String header = requestContext.getHeaderString("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            abort(requestContext, "Session requise");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        AuthenticatedUser user = sessionStore.validate(token);
        if (user == null) {
            abort(requestContext, "Session invalide ou expirée");
            return;
        }

        String[] requiredRoles = requiredRoles();
        if (requiredRoles.length > 0 && Arrays.stream(requiredRoles).noneMatch(user::hasRole)) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"erreur\":\"Accès non autorisé pour ce rôle\"}").build());
            return;
        }

        // Contrôle fin par permission (menu, action) lorsqu'il est déclaré sur la ressource.
        Secured perm = permissionRequise();
        if (perm != null && !perm.menu().isEmpty()
                && !permissionService.autorise(user.getRoles(), perm.menu(), perm.action())) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"erreur\":\"Action non autorisée : " + perm.action()
                            + " sur " + perm.menu() + "\"}").build());
            return;
        }

        capturerContexte();
        requestContext.setSecurityContext(new UserSecurityContext(user));
    }

    /** Renseigne RequestContext (IP réelle + nom de poste) pour la journalisation. */
    private void capturerContexte() {
        if (servletRequest == null) { return; }
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            int virgule = ip.indexOf(',');
            ip = (virgule > 0 ? ip.substring(0, virgule) : ip).trim();
        } else {
            ip = servletRequest.getRemoteAddr();
        }
        String poste = servletRequest.getRemoteHost();
        if (poste != null && poste.equals(servletRequest.getRemoteAddr())) { poste = null; }
        RequestContext.definir(ip, poste);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        RequestContext.effacer();
    }

    private String[] requiredRoles() {
        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(Secured.class)) {
            return method.getAnnotation(Secured.class).roles();
        }
        Class<?> clazz = resourceInfo.getResourceClass();
        if (clazz != null && clazz.isAnnotationPresent(Secured.class)) {
            return clazz.getAnnotation(Secured.class).roles();
        }
        return new String[0];
    }

    /** @Secured portant un menu/action (méthode prioritaire sur la classe). */
    private Secured permissionRequise() {
        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(Secured.class)) {
            Secured s = method.getAnnotation(Secured.class);
            if (!s.menu().isEmpty()) { return s; }
        }
        Class<?> clazz = resourceInfo.getResourceClass();
        if (clazz != null && clazz.isAnnotationPresent(Secured.class)) {
            Secured s = clazz.getAnnotation(Secured.class);
            if (!s.menu().isEmpty()) { return s; }
        }
        return null;
    }

    private void abort(ContainerRequestContext ctx, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"erreur\":\"" + message + "\"}").build());
    }

    private static class UserSecurityContext implements SecurityContext {
        private final AuthenticatedUser user;
        UserSecurityContext(AuthenticatedUser user) { this.user = user; }
        @Override public Principal getUserPrincipal() { return user::getLogin; }
        @Override public boolean isUserInRole(String role) { return user.hasRole(role); }
        @Override public boolean isSecure() { return false; }
        @Override public String getAuthenticationScheme() { return "Bearer"; }
    }
}
