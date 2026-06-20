package com.ubisenderpro.service;

import com.ubisenderpro.entity.Opportunite;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Stateless
public class OpportuniteService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Opportunite> lister(String statut, Long agentId) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM Opportunite o WHERE 1=1");
        if (statut != null && !statut.isEmpty()) jpql.append(" AND o.statut = :s");
        if (agentId != null) jpql.append(" AND o.agentId = :a");
        jpql.append(" ORDER BY o.updatedAt DESC, o.createdAt DESC");
        var q = em.createQuery(jpql.toString(), Opportunite.class);
        if (statut != null && !statut.isEmpty()) q.setParameter("s", statut);
        if (agentId != null) q.setParameter("a", agentId);
        return q.getResultList();
    }

    public Optional<Opportunite> parId(Long id) { return Optional.ofNullable(em.find(Opportunite.class, id)); }

    public Opportunite creer(Opportunite o) { em.persist(o); return o; }
    public Opportunite modifier(Opportunite o) { return em.merge(o); }

    public Opportunite changerStatut(Long id, String statut) {
        Opportunite o = em.find(Opportunite.class, id);
        if (o == null) return null;
        o.setStatut(statut);
        return em.merge(o);
    }
}
