package com.ubisenderpro.service;

import com.ubisenderpro.entity.ImportDetail;
import com.ubisenderpro.entity.ImportLog;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/**
 * Consultation du journal d'import et export des lignes rejetées (section 25.6).
 */
@Stateless
public class ImportQueryService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<ImportLog> lister(int offset, int limit) {
        return em.createQuery("SELECT i FROM ImportLog i ORDER BY i.createdAt DESC", ImportLog.class)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public Optional<ImportLog> parId(Long id) { return Optional.ofNullable(em.find(ImportLog.class, id)); }

    public List<ImportDetail> details(Long importId, String statut) {
        String jpql = "SELECT d FROM ImportDetail d WHERE d.importId = :i" +
                (statut != null && !statut.isEmpty() ? " AND d.statut = :s" : "") +
                " ORDER BY d.numeroLigne";
        var q = em.createQuery(jpql, ImportDetail.class).setParameter("i", importId);
        if (statut != null && !statut.isEmpty()) q.setParameter("s", statut);
        return q.getResultList();
    }

    /** Construit un CSV des lignes rejetées/ignorées téléchargeable par l'utilisateur. */
    public String csvRejets(Long importId) {
        StringBuilder sb = new StringBuilder("numero_ligne;statut;message;contenu\n");
        for (ImportDetail d : details(importId, null)) {
            sb.append(d.getNumeroLigne()).append(';')
              .append(echapper(d.getStatut())).append(';')
              .append(echapper(d.getMessage())).append(';')
              .append(echapper(d.getContenuLigne())).append('\n');
        }
        return sb.toString();
    }

    private String echapper(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        return (s.contains(";") || s.contains("\n") || s.contains("\"")) ? "\"" + s + "\"" : s;
    }
}
