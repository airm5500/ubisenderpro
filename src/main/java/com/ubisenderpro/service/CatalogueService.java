package com.ubisenderpro.service;

import com.ubisenderpro.entity.CategorieArticle;
import com.ubisenderpro.entity.Marque;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des catégories et marques, avec résolution tolérante des libellés
 * pour les imports (création si absente selon autorisation).
 */
@Stateless
public class CatalogueService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<CategorieArticle> listerCategories() {
        return em.createQuery("SELECT c FROM CategorieArticle c ORDER BY c.libelle", CategorieArticle.class).getResultList();
    }

    public List<Marque> listerMarques() {
        return em.createQuery("SELECT m FROM Marque m ORDER BY m.nom", Marque.class).getResultList();
    }

    public CategorieArticle creerCategorie(CategorieArticle c) { em.persist(c); return c; }
    public CategorieArticle modifierCategorie(CategorieArticle c) { return em.merge(c); }
    public Marque creerMarque(Marque m) { em.persist(m); return m; }
    public Marque modifierMarque(Marque m) { return em.merge(m); }

    public Optional<CategorieArticle> resoudreCategorie(String libelle, boolean creer) {
        if (libelle == null || libelle.trim().isEmpty()) return Optional.empty();
        String cle = norm(libelle);
        for (CategorieArticle c : listerCategories()) {
            if (norm(c.getLibelle()).equals(cle) || norm(c.getCode()).equals(cle)) return Optional.of(c);
        }
        if (!creer) return Optional.empty();
        CategorieArticle c = new CategorieArticle();
        c.setCode(cle.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        c.setLibelle(libelle.trim());
        em.persist(c);
        return Optional.of(c);
    }

    public Optional<Marque> resoudreMarque(String nom, boolean creer) {
        if (nom == null || nom.trim().isEmpty()) return Optional.empty();
        String cle = norm(nom);
        for (Marque m : listerMarques()) {
            if (norm(m.getNom()).equals(cle) || norm(m.getCode()).equals(cle)) return Optional.of(m);
        }
        if (!creer) return Optional.empty();
        Marque m = new Marque();
        m.setCode(cle.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        m.setNom(nom.trim());
        em.persist(m);
        return Optional.of(m);
    }

    private String norm(String v) {
        return Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase().replaceAll("\\s+", "");
    }
}
