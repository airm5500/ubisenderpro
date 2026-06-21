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

    /** Journal d'actions le plus récent (consultation administrateur). */
    public List<JournalAction> lister(int limit) {
        return em.createQuery("SELECT j FROM JournalAction j ORDER BY j.createdAt DESC", JournalAction.class)
                .setMaxResults(limit <= 0 ? 200 : limit).getResultList();
    }
}
