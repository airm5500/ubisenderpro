package com.ubisenderpro.service;

import com.ubisenderpro.entity.JournalAction;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Journalise les opérations sensibles (objectif 24 et section 26.3 de la spec).
 */
@Stateless
public class JournalService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void tracer(Long utilisateurId, String login, String action,
                       String entite, Long entiteId, String details, String ip) {
        JournalAction ja = new JournalAction();
        ja.setUtilisateurId(utilisateurId);
        ja.setLogin(login);
        ja.setAction(action);
        ja.setEntite(entite);
        ja.setEntiteId(entiteId);
        ja.setDetails(details);
        ja.setAdresseIp(ip);
        em.persist(ja);
    }

    /** Journal d'actions le plus récent (hors navigation, gardée pour le détail de session). */
    public List<JournalAction> lister(int limit) {
        return em.createQuery("SELECT j FROM JournalAction j WHERE j.action <> 'NAVIGATION' " +
                "ORDER BY j.createdAt DESC", JournalAction.class)
                .setMaxResults(limit <= 0 ? 200 : limit).getResultList();
    }

    /** Activité (menus parcourus + actions) d'un utilisateur sur une fenêtre de temps (session). */
    public List<JournalAction> listerActivite(String login, java.time.LocalDateTime debut, java.time.LocalDateTime fin) {
        StringBuilder q = new StringBuilder("SELECT j FROM JournalAction j WHERE j.login = :l");
        if (debut != null) { q.append(" AND j.createdAt >= :d"); }
        if (fin != null) { q.append(" AND j.createdAt <= :f"); }
        q.append(" ORDER BY j.createdAt ASC");
        javax.persistence.TypedQuery<JournalAction> query = em.createQuery(q.toString(), JournalAction.class)
                .setParameter("l", login);
        if (debut != null) { query.setParameter("d", debut); }
        if (fin != null) { query.setParameter("f", fin); }
        return query.setMaxResults(1000).getResultList();
    }
}
