package com.ubisenderpro.security;

import java.util.Set;

/**
 * Représente l'utilisateur authentifié associé à un jeton de session.
 */
public class AuthenticatedUser {

    private final Long id;
    private final String login;
    private final String nomComplet;
    private final Set<String> roles;

    public AuthenticatedUser(Long id, String login, String nomComplet, Set<String> roles) {
        this.id = id;
        this.login = login;
        this.nomComplet = nomComplet;
        this.roles = roles;
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getNomComplet() { return nomComplet; }
    public Set<String> getRoles() { return roles; }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
