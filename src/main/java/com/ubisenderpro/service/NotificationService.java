package com.ubisenderpro.service;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centre de notifications : agrège, par type, les éléments récents (7 jours)
 * créés dans l'application, plus les discussions non lues. Chaque groupe porte
 * la vue cible pour la redirection au clic.
 */
@Stateless
public class NotificationService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    private static final int JOURS = 7;
    private static final int MAX_ITEMS = 8;

    public Map<String, Object> resume() {
        LocalDateTime depuis = LocalDateTime.now().minusDays(JOURS);
        List<Map<String, Object>> groupes = new ArrayList<>();
        groupes.add(groupe("PROMOTION", "Promotions créées", "promotions",
                "SELECT p.id, p.nom, p.createdAt FROM Promotion p WHERE p.createdAt >= :d ORDER BY p.createdAt DESC", depuis));
        groupes.add(groupe("INFORMATION", "Informations créées", "infos",
                "SELECT i.id, i.titre, i.createdAt FROM InfoEvenement i WHERE i.createdAt >= :d ORDER BY i.createdAt DESC", depuis));
        groupes.add(groupe("EVENEMENT", "Événements dispo/rupture créés", "dispo",
                "SELECT e.id, e.titre, e.createdAt FROM DispoEvenement e WHERE e.createdAt >= :d ORDER BY e.createdAt DESC", depuis));
        groupes.add(groupe("CAMPAGNE", "Campagnes créées", "campaigns",
                "SELECT c.id, c.nom, c.createdAt FROM Campagne c WHERE c.createdAt >= :d ORDER BY c.createdAt DESC", depuis));
        groupes.add(groupe("RELANCE", "Relances envoyées", "recouvrement",
                "SELECT r.id, r.destinataire, r.createdAt FROM RecEnvoi r WHERE r.createdAt >= :d ORDER BY r.createdAt DESC", depuis));
        groupes.add(groupeDiscussions());

        long total = 0;
        for (Map<String, Object> g : groupes) { total += ((Number) g.get("count")).longValue(); }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total);
        res.put("groupes", groupes);
        return res;
    }

    private Map<String, Object> groupe(String type, String libelle, String vue, String jpql, LocalDateTime depuis) {
        List<Object[]> rows = em.createQuery(jpql, Object[].class)
                .setParameter("d", depuis).setMaxResults(MAX_ITEMS).getResultList();
        long total = compter(type, depuis);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("libelle", r[1] == null ? "" : String.valueOf(r[1]));
            m.put("date", r[2]);
            items.add(m);
        }
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", type);
        g.put("titre", libelle);
        g.put("vue", vue);
        g.put("count", total);
        g.put("items", items);
        return g;
    }

    private long compter(String type, LocalDateTime depuis) {
        String jpql;
        switch (type) {
            case "PROMOTION": jpql = "SELECT COUNT(p) FROM Promotion p WHERE p.createdAt >= :d"; break;
            case "INFORMATION": jpql = "SELECT COUNT(i) FROM InfoEvenement i WHERE i.createdAt >= :d"; break;
            case "EVENEMENT": jpql = "SELECT COUNT(e) FROM DispoEvenement e WHERE e.createdAt >= :d"; break;
            case "CAMPAGNE": jpql = "SELECT COUNT(c) FROM Campagne c WHERE c.createdAt >= :d"; break;
            case "RELANCE": jpql = "SELECT COUNT(r) FROM RecEnvoi r WHERE r.createdAt >= :d"; break;
            default: return 0;
        }
        return em.createQuery(jpql, Long.class).setParameter("d", depuis).getSingleResult();
    }

    private Map<String, Object> groupeDiscussions() {
        List<Object[]> rows = em.createQuery(
                "SELECT c.id, c.nomAffiche, c.dernierMessage FROM Conversation c WHERE c.nonLu > 0 " +
                "ORDER BY c.dateDernierMessage DESC", Object[].class).setMaxResults(MAX_ITEMS).getResultList();
        long total = em.createQuery("SELECT COUNT(c) FROM Conversation c WHERE c.nonLu > 0", Long.class).getSingleResult();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("libelle", (r[1] == null ? "" : r[1]) + (r[2] == null ? "" : " — " + r[2]));
            items.add(m);
        }
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", "DISCUSSION");
        g.put("titre", "Discussions non lues");
        g.put("vue", "inbox");
        g.put("count", total);
        g.put("items", items);
        return g;
    }
}
