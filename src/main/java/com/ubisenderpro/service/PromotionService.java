package com.ubisenderpro.service;

import com.ubisenderpro.entity.Promotion;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des promotions (définies une fois, associées à plusieurs produits).
 */
@Stateless
public class PromotionService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Promotion> lister() {
        return em.createQuery("SELECT p FROM Promotion p ORDER BY p.actif DESC, p.nom", Promotion.class)
                .getResultList();
    }

    public Optional<Promotion> parId(Long id) { return Optional.ofNullable(em.find(Promotion.class, id)); }

    public Optional<Promotion> parCode(String code) {
        if (code == null || code.trim().isEmpty()) { return Optional.empty(); }
        List<Promotion> l = em.createQuery("SELECT p FROM Promotion p WHERE p.code = :c", Promotion.class)
                .setParameter("c", code.trim()).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public Promotion creer(Promotion p) {
        if (p.getCode() == null || p.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code de la promotion est obligatoire");
        }
        if (p.getNom() == null || p.getNom().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom de la promotion est obligatoire");
        }
        if (parCode(p.getCode()).isPresent()) {
            throw new IllegalArgumentException("Une promotion avec le code « " + p.getCode() + " » existe déjà");
        }
        em.persist(p);
        return p;
    }

    public Promotion modifier(Promotion p) {
        Promotion ex = em.find(Promotion.class, p.getId());
        if (ex == null) { throw new IllegalArgumentException("Promotion introuvable"); }
        ex.setCode(p.getCode());
        ex.setNom(p.getNom());
        ex.setDescription(p.getDescription());
        ex.setDateDebut(p.getDateDebut());
        ex.setDateFin(p.getDateFin());
        ex.setActif(p.isActif());
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }

    /** Résout une promotion par code, la crée si absente (import). */
    public Promotion resoudre(String code, String nom, LocalDateTime debut, LocalDateTime fin) {
        if (code == null || code.trim().isEmpty()) { return null; }
        Optional<Promotion> ex = parCode(code);
        Promotion p = ex.orElseGet(Promotion::new);
        boolean creation = !ex.isPresent();
        p.setCode(code.trim());
        if (nom != null && !nom.isEmpty()) { p.setNom(nom); }
        else if (creation) { p.setNom(code.trim()); }
        if (debut != null) { p.setDateDebut(debut); }
        if (fin != null) { p.setDateFin(fin); }
        if (creation) { em.persist(p); } else { em.merge(p); }
        return p;
    }
}
