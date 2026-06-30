package com.ubisenderpro.service;

import com.ubisenderpro.entity.Utilisateur;
import com.ubisenderpro.security.AuthenticatedUser;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Cloisonnement multi-agences du module Recouvrement : détermine l'agence à
 * laquelle un utilisateur est limité. {@code null} = aucune restriction
 * (rôle ADMIN, droit de vue consolidée VOIR_GROUPE, ou agence non renseignée).
 */
@Stateless
public class RecScopeService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private PermissionService permissionService;

    public String agencePortee(AuthenticatedUser u) {
        if (u == null) { return null; }
        if (u.getRoles() != null && u.getRoles().contains("ADMIN")) { return null; }
        if (permissionService.autorise(u.getRoles(), "recouvrement", "VOIR_GROUPE")) { return null; }
        Utilisateur ut = em.find(Utilisateur.class, u.getId());
        String ag = ut == null ? null : ut.getAgence();
        return (ag == null || ag.trim().isEmpty()) ? null : ag.trim();
    }
}
