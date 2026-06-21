package com.ubisenderpro.service;

import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Journalisation des conversations/messages du canal WhatsApp Web (canal = WEB),
 * afin de les afficher dans la même boîte de réception que le canal API.
 */
@Stateless
public class WaWebJournalService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /** Retrouve ou crée la conversation WEB d'une session pour un numéro. */
    public Conversation conversation(Long sessionId, String numero, String nomAffiche) {
        List<Conversation> l = em.createQuery(
                "SELECT c FROM Conversation c WHERE c.canal = 'WEB' AND c.waWebSessionId = :s AND c.numeroWhatsapp = :n",
                Conversation.class)
                .setParameter("s", sessionId).setParameter("n", numero)
                .setMaxResults(1).getResultList();
        if (!l.isEmpty()) {
            Conversation c = l.get(0);
            if (nomAffiche != null && !nomAffiche.isEmpty() && (c.getNomAffiche() == null || c.getNomAffiche().isEmpty())) {
                c.setNomAffiche(nomAffiche); em.merge(c);
            }
            return c;
        }
        Conversation c = new Conversation();
        c.setCanal("WEB");
        c.setWaWebSessionId(sessionId);
        c.setNumeroWhatsapp(numero);
        c.setNomAffiche(nomAffiche);
        c.setStatut("OUVERTE");
        em.persist(c);
        em.flush();
        return c;
    }

    /** Enregistre un message sortant WEB dans la conversation. */
    public void enregistrerSortant(Long sessionId, String numero, String type, String contenu, String waMessageId) {
        Conversation conv = conversation(sessionId, numero, null);
        Message m = new Message();
        m.setConversationId(conv.getId());
        m.setDirection("SORTANT");
        m.setTypeMessage(type == null ? "TEXTE" : type.toUpperCase());
        m.setContenu(contenu);
        m.setStatut("ENVOYE");
        m.setWaMessageId(waMessageId);
        em.persist(m);
        conv.setDernierMessage(contenu);
        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
    }

    /** Enregistre un message entrant WEB et incrémente le compteur non lu. */
    public void enregistrerEntrant(Long sessionId, String numero, String nomAffiche,
                                   String type, String contenu, String waMessageId) {
        Conversation conv = conversation(sessionId, numero, nomAffiche);
        Message m = new Message();
        m.setConversationId(conv.getId());
        m.setDirection("ENTRANT");
        m.setTypeMessage(type == null ? "TEXTE" : type.toUpperCase());
        m.setContenu(contenu);
        m.setStatut("RECU");
        m.setWaMessageId(waMessageId);
        em.persist(m);
        conv.setDernierMessage(contenu);
        conv.setDateDernierMessage(LocalDateTime.now());
        conv.setNonLu(conv.getNonLu() + 1);
        if (!"OUVERTE".equals(conv.getStatut())) { conv.setStatut("OUVERTE"); }
        em.merge(conv);
    }
}
