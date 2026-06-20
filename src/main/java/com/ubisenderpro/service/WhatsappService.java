package com.ubisenderpro.service;

import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.WhatsappAccount;
import com.ubisenderpro.whatsapp.WhatsappCloudClient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des comptes WhatsApp et envoi de messages via la Cloud API.
 */
@Stateless
public class WhatsappService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<WhatsappAccount> listerComptes() {
        return em.createQuery("SELECT w FROM WhatsappAccount w ORDER BY w.libelle", WhatsappAccount.class).getResultList();
    }

    public Optional<WhatsappAccount> compte(Long id) { return Optional.ofNullable(em.find(WhatsappAccount.class, id)); }

    public WhatsappAccount creerCompte(WhatsappAccount a) { em.persist(a); return a; }
    public WhatsappAccount modifierCompte(WhatsappAccount a) { return em.merge(a); }

    public Optional<WhatsappAccount> compteParPhoneNumberId(String phoneNumberId) {
        List<WhatsappAccount> l = em.createQuery(
                "SELECT w FROM WhatsappAccount w WHERE w.phoneNumberId = :p", WhatsappAccount.class)
                .setParameter("p", phoneNumberId).setMaxResults(1).getResultList();
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /**
     * Retrouve ou crée la conversation pour un numéro donné sur un compte donné.
     */
    public Conversation conversationPour(Long accountId, String numero, String nomAffiche) {
        List<Conversation> l = em.createQuery(
                "SELECT c FROM Conversation c WHERE c.whatsappAccountId = :a AND c.numeroWhatsapp = :n",
                Conversation.class)
                .setParameter("a", accountId).setParameter("n", numero)
                .setMaxResults(1).getResultList();
        if (!l.isEmpty()) return l.get(0);
        Conversation c = new Conversation();
        c.setWhatsappAccountId(accountId);
        c.setNumeroWhatsapp(numero);
        c.setNomAffiche(nomAffiche);
        c.setStatut("OUVERTE");
        em.persist(c);
        return c;
    }

    /**
     * Envoie un message texte sortant dans une conversation et enregistre le suivi.
     */
    public Message envoyerTexte(Long accountId, String numero, String texte, Long expediteurId) {
        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");

        Conversation conv = conversationPour(accountId, numero, null);

        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setDirection("SORTANT");
        msg.setTypeMessage("TEXTE");
        msg.setContenu(texte);
        msg.setExpediteurId(expediteurId);

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerTexte(numero, texte);
        if (res.success) {
            msg.setStatut("ENVOYE");
            msg.setWaMessageId(res.waMessageId);
        } else {
            msg.setStatut("ECHOUE");
            msg.setErreur(res.erreur);
        }
        em.persist(msg);

        conv.setDernierMessage(texte);
        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
        return msg;
    }

    public Message envoyerMedia(Long accountId, String numero, String type, String url, String legende, Long expediteurId) {
        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        if (account == null) throw new IllegalArgumentException("Compte WhatsApp introuvable");
        Conversation conv = conversationPour(accountId, numero, null);

        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setDirection("SORTANT");
        msg.setTypeMessage(type.toUpperCase());
        msg.setContenu(legende != null ? legende : url);
        msg.setExpediteurId(expediteurId);

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerMedia(numero, type, url, legende);
        if (res.success) { msg.setStatut("ENVOYE"); msg.setWaMessageId(res.waMessageId); }
        else { msg.setStatut("ECHOUE"); msg.setErreur(res.erreur); }
        em.persist(msg);

        conv.setDateDernierMessage(LocalDateTime.now());
        em.merge(conv);
        return msg;
    }
}
