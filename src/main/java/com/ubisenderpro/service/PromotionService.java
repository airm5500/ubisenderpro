package com.ubisenderpro.service;

import com.ubisenderpro.entity.Promotion;
import com.ubisenderpro.entity.PromotionProduit;

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

    /** Liste filtrée par statut (module Promotions : onglets actives/programmées/inactives/archivées). */
    public List<Promotion> listerParStatut(String statut) {
        if (statut == null || statut.isEmpty()) { return lister(); }
        return em.createQuery(
                "SELECT p FROM Promotion p WHERE p.statut = :s ORDER BY p.dateDebut DESC, p.nom", Promotion.class)
                .setParameter("s", statut).getResultList();
    }

    /** Statut déduit des dates (hors ANNULEE/ARCHIVEE qui sont des décisions manuelles). */
    public String statutAuto(Promotion p) {
        LocalDateTime now = LocalDateTime.now();
        if (p.getDateDebut() != null && p.getDateDebut().isAfter(now)) { return "PROGRAMMEE"; }
        if (p.getDateFin() != null && p.getDateFin().isBefore(now)) { return "INACTIVE"; }
        return "ACTIVE";
    }

    /** Recalcule les statuts automatiques (appelé par l'ordonnanceur). */
    public int rafraichirStatuts() {
        int n = 0;
        for (Promotion p : lister()) {
            if ("ANNULEE".equals(p.getStatut()) || "ARCHIVEE".equals(p.getStatut())) { continue; }
            String auto = statutAuto(p);
            boolean actif = "ACTIVE".equals(auto);
            if (!auto.equals(p.getStatut()) || p.isActif() != actif) {
                p.setStatut(auto);
                p.setActif(actif);
                em.merge(p);
                n++;
            }
        }
        return n;
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
        if (p.getDateDebut() != null && p.getDateFin() != null && p.getDateFin().isBefore(p.getDateDebut())) {
            throw new ValidationException("dateFin", "La date de fin doit être postérieure ou égale à la date de début.");
        }
        if (p.getStatut() == null || p.getStatut().isEmpty()) { p.setStatut(statutAuto(p)); }
        p.setActif("ACTIVE".equals(p.getStatut()));
        em.persist(p);
        return p;
    }

    public Promotion modifier(Promotion p) {
        Promotion ex = em.find(Promotion.class, p.getId());
        if (ex == null) { throw new IllegalArgumentException("Promotion introuvable"); }
        if (p.getDateDebut() != null && p.getDateFin() != null && p.getDateFin().isBefore(p.getDateDebut())) {
            throw new ValidationException("dateFin", "La date de fin doit être postérieure ou égale à la date de début.");
        }
        ex.setCode(p.getCode());
        ex.setNom(p.getNom());
        ex.setDescription(p.getDescription());
        ex.setDateDebut(p.getDateDebut());
        ex.setDateFin(p.getDateFin());
        ex.setResponsable(p.getResponsable());
        // Statut recalculé sauf décision manuelle (ANNULEE/ARCHIVEE conservées).
        if (!"ANNULEE".equals(ex.getStatut()) && !"ARCHIVEE".equals(ex.getStatut())) {
            ex.setStatut(statutAuto(ex));
            ex.setActif("ACTIVE".equals(ex.getStatut()));
        }
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    /** Annule une promotion (arrêt avant terme) sans perdre l'historique. */
    public Promotion annuler(Long id) { return changerStatut(id, "ANNULEE"); }

    /** Archive une promotion (retirée des vues opérationnelles, historique conservé). */
    public Promotion archiver(Long id) { return changerStatut(id, "ARCHIVEE"); }

    private Promotion changerStatut(Long id, String statut) {
        Promotion p = em.find(Promotion.class, id);
        if (p == null) { return null; }
        p.setStatut(statut);
        p.setActif(false);
        p.setUpdatedAt(LocalDateTime.now());
        return em.merge(p);
    }

    /** Duplique une promotion et ses produits (nouveau code « -COPIE »), en PROGRAMMEE/auto. */
    public Promotion dupliquer(Long id) {
        Promotion src = em.find(Promotion.class, id);
        if (src == null) { return null; }
        Promotion c = new Promotion();
        String code = src.getCode() + "-COPIE";
        int i = 2;
        while (parCode(code).isPresent()) { code = src.getCode() + "-COPIE" + (i++); }
        c.setCode(code);
        c.setNom(src.getNom() + " (copie)");
        c.setDescription(src.getDescription());
        c.setDateDebut(src.getDateDebut());
        c.setDateFin(src.getDateFin());
        c.setResponsable(src.getResponsable());
        c.setStatut(statutAuto(c));
        c.setActif("ACTIVE".equals(c.getStatut()));
        em.persist(c);
        for (PromotionProduit pp : em.createQuery(
                "SELECT pp FROM PromotionProduit pp WHERE pp.promotionId = :id", PromotionProduit.class)
                .setParameter("id", id).getResultList()) {
            PromotionProduit n = new PromotionProduit();
            n.setPromotionId(c.getId());
            n.setArticleId(pp.getArticleId());
            n.setCip7(pp.getCip7()); n.setCip13(pp.getCip13()); n.setNomProduit(pp.getNomProduit());
            n.setQuantiteMinimale(pp.getQuantiteMinimale());
            n.setTauxUg(pp.getTauxUg()); n.setTauxMaxUg(pp.getTauxMaxUg());
            n.setQuantiteUg(pp.getQuantiteUg()); n.setQuantiteUgMax(pp.getQuantiteUgMax());
            n.setModeCalcul(pp.getModeCalcul()); n.setActif(pp.isActif());
            em.persist(n);
        }
        return c;
    }

    public void supprimer(Long id) {
        em.createQuery("DELETE FROM PromotionProduit pp WHERE pp.promotionId = :id")
                .setParameter("id", id).executeUpdate();
        parId(id).ifPresent(em::remove);
    }

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
