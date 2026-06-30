package com.ubisenderpro.service;

import com.ubisenderpro.entity.BotFaq;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD de la base de connaissance du bot (usp_bot_faq).
 */
@Stateless
public class BotFaqService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<BotFaq> lister() {
        return em.createQuery("SELECT f FROM BotFaq f ORDER BY f.ordre, f.id", BotFaq.class).getResultList();
    }

    /** Entrées actives, pour le moteur du bot. */
    public List<BotFaq> listerActives() {
        return em.createQuery("SELECT f FROM BotFaq f WHERE f.actif = true ORDER BY f.ordre, f.id", BotFaq.class)
                .getResultList();
    }

    public BotFaq creer(BotFaq f) {
        if (f.getDeclencheurs() == null || f.getDeclencheurs().trim().isEmpty()) {
            throw new ValidationException("declencheurs", "Au moins un mot-clé déclencheur est obligatoire.");
        }
        if (f.getReponse() == null || f.getReponse().trim().isEmpty()) {
            throw new ValidationException("reponse", "La réponse est obligatoire.");
        }
        em.persist(f);
        return f;
    }

    public BotFaq modifier(Long id, BotFaq data) {
        BotFaq f = em.find(BotFaq.class, id);
        if (f == null) { return null; }
        if (data.getDeclencheurs() != null) { f.setDeclencheurs(data.getDeclencheurs()); }
        if (data.getReponse() != null) { f.setReponse(data.getReponse()); }
        f.setOrdre(data.getOrdre());
        f.setActif(data.isActif());
        f.setUpdatedAt(LocalDateTime.now());
        return em.merge(f);
    }

    public void supprimer(Long id) {
        BotFaq f = em.find(BotFaq.class, id);
        if (f != null) { em.remove(f); }
    }
}
