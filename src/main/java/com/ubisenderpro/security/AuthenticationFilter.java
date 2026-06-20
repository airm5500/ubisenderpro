package com.ubisenderpro.security;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
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
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    private SessionStore sessionStore;

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

        requestContext.setSecurityContext(new UserSecurityContext(user));
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
