package com.ubisenderpro.service;

import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.SessionStore;

import javax.ejb.EJB;
import javax.ejb.Stateless;

/**
 * Traçage métier centralisé : résout l'utilisateur courant depuis le jeton
 * et délègue au journal d'actions. Best-effort (ne bloque jamais l'opération).
 */
@Stateless
public class AuditService {

    @EJB
    private JournalService journalService;
    @EJB
    private SessionStore sessionStore;

    public void tracer(String authHeader, String action, String entite, Long entiteId, String details) {
        try {
            AuthenticatedUser u = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                u = sessionStore.validate(authHeader.substring("Bearer ".length()).trim());
            }
            journalService.tracer(u == null ? null : u.getId(), u == null ? null : u.getLogin(),
                    action, entite, entiteId, details, null);
        } catch (Exception ignore) { /* traçage non bloquant */ }
    }
}
