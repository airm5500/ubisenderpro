package com.ubisenderpro.service;

import com.ubisenderpro.entity.Role;
import com.ubisenderpro.entity.Utilisateur;
import com.ubisenderpro.security.AuthenticatedUser;
import com.ubisenderpro.security.PasswordHasher;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Stateless
public class AuthService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /**
     * Vérifie les identifiants et renvoie l'utilisateur authentifié si valides.
     */
    public Optional<AuthenticatedUser> authentifier(String login, String motDePasse) {
        Utilisateur u;
        try {
            u = em.createQuery(
                    "SELECT u FROM Utilisateur u WHERE u.login = :login AND u.actif = true",
                    Utilisateur.class)
                    .setParameter("login", login)
                    .getSingleResult();
        } catch (NoResultException e) {
            return Optional.empty();
        }

        if (!PasswordHasher.verify(motDePasse, u.getMotDePasseHash())) {
            return Optional.empty();
        }

        u.setDerniereConnexion(LocalDateTime.now());
        em.merge(u);

        return Optional.of(new AuthenticatedUser(
                u.getId(), u.getLogin(), u.getNomComplet(),
                u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet())));
    }
}
