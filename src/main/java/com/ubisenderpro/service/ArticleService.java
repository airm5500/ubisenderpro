package com.ubisenderpro.service;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Article;
import com.ubisenderpro.entity.MouvementStock;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Stateless
public class ArticleService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public PageResult<Article> rechercher(String q, Long categorieId, Long marqueId,
                                          Boolean actif, int offset, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        if (q != null && !q.isEmpty()) {
            where.append(" AND (LOWER(a.designation) LIKE :q OR LOWER(a.pscode) LIKE :q" +
                    " OR a.codeBarres LIKE :q OR a.cip LIKE :q OR LOWER(a.codePromo) LIKE :q)");
            params.add(new Object[]{"q", "%" + q.toLowerCase() + "%"});
        }
        if (categorieId != null) { where.append(" AND a.categorieId = :cat"); params.add(new Object[]{"cat", categorieId}); }
        if (marqueId != null) { where.append(" AND a.marqueId = :mar"); params.add(new Object[]{"mar", marqueId}); }
        if (actif != null) { where.append(" AND a.actif = :act"); params.add(new Object[]{"act", actif}); }

        TypedQuery<Article> query = em.createQuery("SELECT a FROM Article a" + where + " ORDER BY a.designation", Article.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(a) FROM Article a" + where, Long.class);
        for (Object[] p : params) { query.setParameter((String) p[0], p[1]); count.setParameter((String) p[0], p[1]); }

        return new PageResult<>(query.setFirstResult(offset).setMaxResults(limit).getResultList(), count.getSingleResult());
    }

    public Optional<Article> parId(Long id) { return Optional.ofNullable(em.find(Article.class, id)); }

    public Optional<Article> parCode(String code) {
        List<Article> l = em.createQuery("SELECT a FROM Article a WHERE a.pscode = :c", Article.class)
                .setParameter("c", code).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /** Articles portant un code promo donné (aperçu avant mise à jour). */
    public List<Article> parCodePromo(String codePromo) {
        if (codePromo == null || codePromo.trim().isEmpty()) { return new ArrayList<>(); }
        return em.createQuery("SELECT a FROM Article a WHERE a.codePromo = :c ORDER BY a.designation", Article.class)
                .setParameter("c", codePromo.trim()).getResultList();
    }

    /**
     * Mise à jour sélective des dates d'une promotion : applique les dates à tous
     * les articles portant ce code promo. @return le nombre d'articles mis à jour.
     */
    public int majDatesPromo(String codePromo, LocalDateTime debut, LocalDateTime fin) {
        if (codePromo == null || codePromo.trim().isEmpty()) {
            throw new IllegalArgumentException("Code promo requis");
        }
        return em.createQuery("UPDATE Article a SET a.dateDebutPromotion = :d, a.dateFinPromotion = :f, " +
                "a.updatedAt = :now WHERE a.codePromo = :c")
                .setParameter("d", debut).setParameter("f", fin)
                .setParameter("now", LocalDateTime.now())
                .setParameter("c", codePromo.trim()).executeUpdate();
    }

    public Article creer(Article a) {
        valider(a, true);
        appliquerPromotions(a);
        em.persist(a);
        return a;
    }

    /**
     * Contrôle des champs obligatoires/cohérence d'un article, messages clairs (#6).
     * Le prix est contrôlé non négatif côté service (sûr pour l'import en masse) ;
     * le formulaire de saisie impose en plus un prix strictement positif.
     */
    private void valider(Article a, boolean creation) {
        if (a.getPscode() == null || a.getPscode().trim().isEmpty()) {
            throw new ValidationException("pscode", "Le PS Code est obligatoire.");
        }
        if (a.getDesignation() == null || a.getDesignation().trim().isEmpty()) {
            throw new ValidationException("designation", "La désignation est obligatoire.");
        }
        if (a.getPrixVente() == null || a.getPrixVente().signum() < 0) {
            throw new ValidationException("prixVente", "Le prix de vente doit être un montant positif.");
        }
        if (a.getPrixPromotionnel() != null && a.getPrixPromotionnel().signum() < 0) {
            throw new ValidationException("prixPromotionnel", "Le prix promotionnel ne peut pas être négatif.");
        }
        // Unicité du PS Code (clé fonctionnelle).
        Optional<Article> existant = parCode(a.getPscode().trim());
        if (existant.isPresent() && (creation || !existant.get().getId().equals(a.getId()))) {
            throw new ValidationException("pscode",
                    "Un article avec le PS Code « " + a.getPscode().trim() + " » existe déjà.");
        }
    }

    /** Résout les promotions depuis promotionIds (écriture). Null = ne pas toucher aux associations. */
    private void appliquerPromotions(Article a) {
        if (a.getPromotionIds() == null) { return; }
        java.util.Set<com.ubisenderpro.entity.Promotion> set = new java.util.HashSet<>();
        for (Long pid : a.getPromotionIds()) {
            com.ubisenderpro.entity.Promotion p = em.find(com.ubisenderpro.entity.Promotion.class, pid);
            if (p != null) { set.add(p); }
        }
        a.setPromotions(set);
    }

    public Article modifier(Article a) {
        valider(a, false);
        appliquerPromotions(a);
        a.setUpdatedAt(LocalDateTime.now());
        return em.merge(a);
    }

    public void supprimer(Long id) { parId(id).ifPresent(em::remove); }

    /** Ajuste le stock indicatif et journalise le mouvement (section 15 de la spec). */
    public Article ajusterStock(Long articleId, BigDecimal nouvelleQuantite, String type,
                                String commentaire, Long utilisateurId) {
        Article a = em.find(Article.class, articleId);
        if (a == null) return null;
        BigDecimal avant = a.getStockDisponible();
        a.setStockDisponible(nouvelleQuantite);
        em.merge(a);

        MouvementStock m = new MouvementStock();
        m.setArticleId(articleId);
        m.setTypeMouvement(type == null ? "AJUSTEMENT_POSITIF" : type);
        m.setQuantiteAvant(avant);
        m.setQuantiteMouvement(nouvelleQuantite.subtract(avant));
        m.setQuantiteApres(nouvelleQuantite);
        m.setSource("MANUEL");
        m.setCommentaire(commentaire);
        m.setUtilisateurId(utilisateurId);
        em.persist(m);
        return a;
    }
}
