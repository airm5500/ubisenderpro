package com.ubisenderpro.service;

import com.ubisenderpro.dto.UserRequest;
import com.ubisenderpro.entity.Role;
import com.ubisenderpro.entity.Utilisateur;
import com.ubisenderpro.security.PasswordHasher;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestion des utilisateurs applicatifs et de leurs rôles (section 26 de la spec).
 */
@Stateless
public class UserService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Role> listerRoles() {
        return em.createQuery("SELECT r FROM Role r WHERE r.actif = true ORDER BY r.libelle", Role.class)
                .getResultList();
    }

    /** Crée un rôle (code normalisé, unique). Le code sert d'identifiant de permission. */
    public Role creerRole(Role r) {
        if (r == null || r.getLibelle() == null || r.getLibelle().trim().isEmpty()) {
            throw new ValidationException("libelle", "Le libellé du rôle est obligatoire.");
        }
        String code = (r.getCode() == null || r.getCode().trim().isEmpty())
                ? r.getLibelle() : r.getCode();
        code = code.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_+|_+$)", "");
        if (code.isEmpty()) {
            throw new ValidationException("code", "Le code du rôle est invalide.");
        }
        if (!em.createQuery("SELECT r FROM Role r WHERE r.code = :c", Role.class)
                .setParameter("c", code).setMaxResults(1).getResultList().isEmpty()) {
            throw new ValidationException("code", "Un rôle avec le code « " + code + " » existe déjà.");
        }
        Role n = new Role();
        n.setCode(code);
        n.setLibelle(r.getLibelle().trim());
        n.setDescription(r.getDescription());
        n.setActif(true);
        em.persist(n);
        return n;
    }

    /** Liste des utilisateurs sans exposer le hash du mot de passe. */
    public List<Map<String, Object>> lister() {
        List<Utilisateur> users = em.createQuery(
                "SELECT u FROM Utilisateur u ORDER BY u.login", Utilisateur.class).getResultList();
        return users.stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> toMap(Utilisateur u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("login", u.getLogin());
        m.put("nomComplet", u.getNomComplet());
        m.put("avatar", u.getAvatar());
        m.put("email", u.getEmail());
        m.put("actif", u.isActif());
        m.put("derniereConnexion", u.getDerniereConnexion());
        m.put("roles", u.getRoles().stream().map(Role::getCode).collect(Collectors.toList()));
        return m;
    }

    public Optional<Utilisateur> parId(Long id) { return Optional.ofNullable(em.find(Utilisateur.class, id)); }

    /** E-mails des superviseurs et administrateurs actifs (notifications d'escalade). */
    public List<String> listerEmailsSuperviseurs() {
        return em.createQuery(
                "SELECT DISTINCT u.email FROM Utilisateur u JOIN u.roles r " +
                "WHERE u.actif = true AND r.code IN ('SUPERVISEUR','ADMIN') " +
                "AND u.email IS NOT NULL AND u.email <> ''", String.class)
                .getResultList();
    }

    /** Liste légère des utilisateurs actifs (id + nom) pour l'affectation de discussions (#5). */
    public List<Map<String, Object>> listerAffectables() {
        return em.createQuery("SELECT u FROM Utilisateur u WHERE u.actif = true ORDER BY u.nomComplet", Utilisateur.class)
                .getResultList().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("nomComplet", u.getNomComplet());
                    return m;
                }).collect(Collectors.toList());
    }

    public boolean loginExiste(String login, Long exclureId) {
        List<Utilisateur> l = em.createQuery(
                "SELECT u FROM Utilisateur u WHERE u.login = :l", Utilisateur.class)
                .setParameter("l", login).getResultList();
        return l.stream().anyMatch(u -> exclureId == null || !u.getId().equals(exclureId));
    }

    public Map<String, Object> creer(UserRequest req) {
        Utilisateur u = new Utilisateur();
        u.setLogin(req.getLogin());
        u.setNomComplet(req.getNomComplet());
        u.setAvatar(req.getAvatar());
        u.setPhoto(req.getPhoto());
        u.setEmail(req.getEmail());
        u.setActif(req.getActif() == null || req.getActif());
        String mdp = (req.getMotDePasse() == null || req.getMotDePasse().isEmpty())
                ? "Change@2026" : req.getMotDePasse();
        u.setMotDePasseHash(PasswordHasher.hash(mdp));
        u.setRoles(resoudreRoles(req.getRoles()));
        em.persist(u);
        return toMap(u);
    }

    public Map<String, Object> modifier(Long id, UserRequest req) {
        Utilisateur u = em.find(Utilisateur.class, id);
        if (u == null) return null;
        if (req.getNomComplet() != null) u.setNomComplet(req.getNomComplet());
        if (req.getAvatar() != null) u.setAvatar(req.getAvatar());
        // Photo : "" efface, valeur = remplace, null = inchangée.
        if (req.getPhoto() != null) u.setPhoto(req.getPhoto().isEmpty() ? null : req.getPhoto());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getActif() != null) u.setActif(req.getActif());
        if (req.getMotDePasse() != null && !req.getMotDePasse().isEmpty()) {
            u.setMotDePasseHash(PasswordHasher.hash(req.getMotDePasse()));
        }
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            u.setRoles(resoudreRoles(req.getRoles()));
        }
        em.merge(u);
        return toMap(u);
    }

    public void definirActif(Long id, boolean actif) {
        parId(id).ifPresent(u -> { u.setActif(actif); em.merge(u); });
    }

    /** Réinitialise le mot de passe (valeur fournie ou « Change@2026 »). @return le mot de passe appliqué. */
    public String reinitialiserMotDePasse(Long id, String nouveau) {
        Utilisateur u = em.find(Utilisateur.class, id);
        if (u == null) { return null; }
        String mdp = (nouveau == null || nouveau.trim().isEmpty()) ? "Change@2026" : nouveau.trim();
        u.setMotDePasseHash(PasswordHasher.hash(mdp));
        em.merge(u);
        return mdp;
    }

    private Set<Role> resoudreRoles(List<String> codes) {
        Set<Role> roles = new HashSet<>();
        if (codes == null) return roles;
        for (String code : codes) {
            List<Role> r = em.createQuery("SELECT r FROM Role r WHERE r.code = :c", Role.class)
                    .setParameter("c", code).setMaxResults(1).getResultList();
            if (!r.isEmpty()) roles.add(r.get(0));
        }
        return roles;
    }
}
