package com.ubisenderpro.service;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Stateless
public class ConversationService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public PageResult<Conversation> lister(String statut, Long agentId, int offset, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (statut != null && !statut.isEmpty()) where.append(" AND c.statut = :statut");
        if (agentId != null) where.append(" AND c.agentId = :agent");

        var q = em.createQuery("SELECT c FROM Conversation c" + where +
                " ORDER BY c.dateDernierMessage DESC", Conversation.class);
        var qc = em.createQuery("SELECT COUNT(c) FROM Conversation c" + where, Long.class);
        if (statut != null && !statut.isEmpty()) { q.setParameter("statut", statut); qc.setParameter("statut", statut); }
        if (agentId != null) { q.setParameter("agent", agentId); qc.setParameter("agent", agentId); }

        return new PageResult<>(q.setFirstResult(offset).setMaxResults(limit).getResultList(), qc.getSingleResult());
    }

    public Optional<Conversation> parId(Long id) { return Optional.ofNullable(em.find(Conversation.class, id)); }

    public List<Message> messages(Long conversationId) {
        return em.createQuery("SELECT m FROM Message m WHERE m.conversationId = :c ORDER BY m.createdAt",
                Message.class).setParameter("c", conversationId).getResultList();
    }

    public void affecter(Long conversationId, Long agentId) {
        parId(conversationId).ifPresent(c -> { c.setAgentId(agentId); em.merge(c); });
    }

    public void changerStatut(Long conversationId, String statut) {
        parId(conversationId).ifPresent(c -> {
            c.setStatut(statut);
            if ("OUVERTE".equals(statut)) c.setNonLu(0);
            em.merge(c);
        });
    }

    public Message ajouterNote(Long conversationId, String note, Long auteurId) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setDirection("SORTANT");
        m.setTypeMessage("NOTE");
        m.setContenu(note);
        m.setNoteInterne(true);
        m.setStatut("ENVOYE");
        m.setExpediteurId(auteurId);
        m.setCreatedAt(LocalDateTime.now());
        em.persist(m);
        return m;
    }

    public void marquerLu(Long conversationId) {
        parId(conversationId).ifPresent(c -> { c.setNonLu(0); em.merge(c); });
    }
}
