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
    public CategorieArticle modifierCategorie(CategorieArticle c) {
        CategorieArticle ex = em.find(CategorieArticle.class, c.getId());
        if (ex == null) { return null; }
        if (c.getCode() != null) { ex.setCode(c.getCode()); }
        if (c.getLibelle() != null) { ex.setLibelle(c.getLibelle()); }
        ex.setDescription(c.getDescription());
        return em.merge(ex);
    }
    public Marque creerMarque(Marque m) { em.persist(m); return m; }
    public Marque modifierMarque(Marque m) {
        Marque ex = em.find(Marque.class, m.getId());
        if (ex == null) { return null; }
        if (m.getCode() != null) { ex.setCode(m.getCode()); }
        if (m.getNom() != null) { ex.setNom(m.getNom()); }
        ex.setDescription(m.getDescription());
        return em.merge(ex);
    }

    /** Supprime une catégorie inutilisée (refus clair si des articles y sont rattachés). */
    public void supprimerCategorie(Long id) {
        Long n = em.createQuery("SELECT COUNT(a) FROM Article a WHERE a.categorieId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (n > 0) {
            throw new ValidationException("categorie",
                    n + " article(s) utilisent cette catégorie : suppression impossible.");
        }
        CategorieArticle c = em.find(CategorieArticle.class, id);
        if (c != null) { em.remove(c); }
    }

    /** Supprime une marque inutilisée (refus clair si des articles y sont rattachés). */
    public void supprimerMarque(Long id) {
        Long n = em.createQuery("SELECT COUNT(a) FROM Article a WHERE a.marqueId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (n > 0) {
            throw new ValidationException("marque",
                    n + " article(s) utilisent cette marque : suppression impossible.");
        }
        Marque m = em.find(Marque.class, id);
        if (m != null) { em.remove(m); }
    }

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
