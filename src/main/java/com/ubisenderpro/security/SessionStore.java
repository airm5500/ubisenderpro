package com.ubisenderpro.security;

import javax.ejb.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage en mémoire des sessions (jeton -> utilisateur authentifié).
 * Authentification par session, conformément à la section 26.3 de la spec.
 */
@Singleton
public class SessionStore {

    private static final Duration EXPIRATION = Duration.ofMinutes(60);

    private static class Session {
        AuthenticatedUser user;
        Instant lastAccess;
        Session(AuthenticatedUser user) {
            this.user = user;
            this.lastAccess = Instant.now();
        }
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(AuthenticatedUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(user));
        return token;
    }

    public AuthenticatedUser validate(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (Duration.between(s.lastAccess, Instant.now()).compareTo(EXPIRATION) > 0) {
            sessions.remove(token);
            return null;
        }
        s.lastAccess = Instant.now();
        return s.user;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Secondes d'inactivité d'une session (sans la rafraîchir) ; -1 si absente/inconnue. */
    public long inactifDepuisSecondes(String token) {
        if (token == null) { return -1; }
        Session s = sessions.get(token);
        if (s == null) { return -1; }
        return Duration.between(s.lastAccess, Instant.now()).getSeconds();
    }
}
