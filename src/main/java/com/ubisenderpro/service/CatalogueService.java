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

    public CategorieArticle creerCategorie(CategorieArticle c) {
        if (c.getCode() == null || c.getCode().trim().isEmpty()) {
            c.setCode(Codes.generer("CAT", this::codeCategorieExiste));
        }
        em.persist(c);
        return c;
    }
    public CategorieArticle modifierCategorie(CategorieArticle c) {
        CategorieArticle ex = em.find(CategorieArticle.class, c.getId());
        if (ex == null) { return null; }
        if (c.getCode() != null) { ex.setCode(c.getCode()); }
        if (c.getLibelle() != null) { ex.setLibelle(c.getLibelle()); }
        ex.setDescription(c.getDescription());
        return em.merge(ex);
    }
    public Marque creerMarque(Marque m) {
        if (m.getCode() == null || m.getCode().trim().isEmpty()) {
            m.setCode(Codes.generer("MARQ", this::codeMarqueExiste));
        }
        em.persist(m);
        return m;
    }

    private boolean codeCategorieExiste(String code) {
        return !em.createQuery("SELECT c FROM CategorieArticle c WHERE c.code = :c", CategorieArticle.class)
                .setParameter("c", code).setMaxResults(1).getResultList().isEmpty();
    }

    private boolean codeMarqueExiste(String code) {
        return !em.createQuery("SELECT m FROM Marque m WHERE m.code = :c", Marque.class)
                .setParameter("c", code).setMaxResults(1).getResultList().isEmpty();
    }
    public Marque modifierMarque(Marque m) {
        Marque ex = em.find(Marque.class, m.getId());
        if (ex == null) { return null; }
        if (m.getCode() != null) { ex.setCode(m.getCode()); }
        if (m.getNom() != null) { ex.setNom(m.getNom()); }
        ex.setDescription(m.getDescription());
        return em.merge(ex);
    }

    /** Supprime une catégorie ; les articles liés sont réaffectés à la catégorie « Standard ». */
    public void supprimerCategorie(Long id) {
        CategorieArticle c = em.find(CategorieArticle.class, id);
        if (c == null) { return; }
        CategorieArticle defaut = categorieStandard();
        if (defaut.getId().equals(id)) {
            throw new ValidationException("categorie", "La catégorie « Standard » ne peut pas être supprimée.");
        }
        em.createQuery("UPDATE Article a SET a.categorieId = :def WHERE a.categorieId = :id")
                .setParameter("def", defaut.getId()).setParameter("id", id).executeUpdate();
        em.remove(c);
    }

    /** Supprime une marque ; les articles liés sont réaffectés à la marque « Standard ». */
    public void supprimerMarque(Long id) {
        Marque m = em.find(Marque.class, id);
        if (m == null) { return; }
        Marque defaut = marqueStandard();
        if (defaut.getId().equals(id)) {
            throw new ValidationException("marque", "La marque « Standard » ne peut pas être supprimée.");
        }
        em.createQuery("UPDATE Article a SET a.marqueId = :def WHERE a.marqueId = :id")
                .setParameter("def", defaut.getId()).setParameter("id", id).executeUpdate();
        em.remove(m);
    }

    /** Catégorie « Standard » d'affectation par défaut (créée si absente). */
    public CategorieArticle categorieStandard() {
        List<CategorieArticle> l = em.createQuery(
                "SELECT c FROM CategorieArticle c WHERE c.code = :code", CategorieArticle.class)
                .setParameter("code", "STANDARD").setMaxResults(1).getResultList();
        if (!l.isEmpty()) { return l.get(0); }
        CategorieArticle c = new CategorieArticle();
        c.setCode("STANDARD");
        c.setLibelle("Standard");
        c.setActif(true);
        em.persist(c);
        em.flush();
        return c;
    }

    /** Marque « Standard » d'affectation par défaut (créée si absente). */
    public Marque marqueStandard() {
        List<Marque> l = em.createQuery(
                "SELECT m FROM Marque m WHERE m.code = :code", Marque.class)
                .setParameter("code", "STANDARD").setMaxResults(1).getResultList();
        if (!l.isEmpty()) { return l.get(0); }
        Marque m = new Marque();
        m.setCode("STANDARD");
        m.setNom("Standard");
        m.setActif(true);
        em.persist(m);
        em.flush();
        return m;
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
