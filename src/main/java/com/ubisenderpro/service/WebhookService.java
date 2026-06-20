package com.ubisenderpro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.Conversation;
import com.ubisenderpro.entity.Message;
import com.ubisenderpro.entity.WebhookEvent;
import com.ubisenderpro.entity.WhatsappAccount;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Réception et traitement des webhooks de la WhatsApp Cloud API :
 *  - messages entrants -> création de conversation + message ENTRANT ;
 *  - statuts (sent/delivered/read) -> mise à jour du message sortant ;
 *  - automatisations simples (STOP -> désabonnement).
 */
@Stateless
public class WebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private WhatsappService whatsappService;
    @EJB
    private ContactService contactService;

    public void traiter(String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setSource("WHATSAPP");
        event.setPayload(payload);
        em.persist(event);

        try {
            JsonNode root = MAPPER.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                    Optional<WhatsappAccount> account = phoneNumberId == null
                            ? Optional.empty() : whatsappService.compteParPhoneNumberId(phoneNumberId);

                    traiterMessagesEntrants(value, account.orElse(null));
                    traiterStatuts(value);
                }
            }
            event.setTraite(true);
            event.setTypeEvent("MESSAGES");
        } catch (Exception e) {
            event.setErreur(e.getMessage());
        }
        em.merge(event);
    }

    private void traiterMessagesEntrants(JsonNode value, WhatsappAccount account) {
        if (account == null) return;
        String nomAffiche = value.path("contacts").path(0).path("profile").path("name").asText(null);

        for (JsonNode m : value.path("messages")) {
            String from = m.path("from").asText();
            String waId = m.path("id").asText(null);
            String type = m.path("type").asText("text");
            String texte = "text".equals(type)
                    ? m.path("text").path("body").asText("")
                    : "[" + type + "]";

            Conversation conv = whatsappService.conversationPour(account.getId(), from, nomAffiche);

            Message msg = new Message();
            msg.setConversationId(conv.getId());
            msg.setDirection("ENTRANT");
            msg.setTypeMessage(type.toUpperCase());
            msg.setContenu(texte);
            msg.setWaMessageId(waId);
            msg.setStatut("RECU");
            em.persist(msg);

            conv.setDernierMessage(texte);
            conv.setDateDernierMessage(LocalDateTime.now());
            conv.setNonLu(conv.getNonLu() + 1);
            // Fenêtre de service client de 24h ouverte par le message entrant.
            conv.setFenetreExpireAt(LocalDateTime.now().plusHours(24));
            if ("CLOTUREE".equals(conv.getStatut())) conv.setStatut("OUVERTE");
            em.merge(conv);

            appliquerAutomatisations(conv, texte);
        }
    }

    private void traiterStatuts(JsonNode value) {
        for (JsonNode s : value.path("statuses")) {
            String waId = s.path("id").asText(null);
            String statut = s.path("status").asText(); // sent, delivered, read, failed
            if (waId == null) continue;

            List<Message> messages = em.createQuery(
                    "SELECT m FROM Message m WHERE m.waMessageId = :id", Message.class)
                    .setParameter("id", waId).getResultList();
            for (Message m : messages) {
                switch (statut) {
                    case "delivered":
                        m.setStatut("DISTRIBUE");
                        m.setDeliveredAt(LocalDateTime.now());
                        break;
                    case "read":
                        m.setStatut("LU");
                        m.setReadAt(LocalDateTime.now());
                        break;
                    case "failed":
                        m.setStatut("ECHOUE");
                        m.setErreur(s.path("errors").path(0).path("title").asText(null));
                        break;
                    default:
                        m.setStatut("ENVOYE");
                }
                em.merge(m);
            }
            // Met aussi à jour le suivi des destinataires de campagne.
            mettreAJourCampagne(waId, statut);
        }
    }

    private void mettreAJourCampagne(String waId, String statut) {
        var dests = em.createQuery(
                "SELECT d FROM CampagneDestinataire d WHERE d.waMessageId = :id",
                com.ubisenderpro.entity.CampagneDestinataire.class)
                .setParameter("id", waId).getResultList();
        for (var d : dests) {
            if ("delivered".equals(statut)) { d.setStatut("DISTRIBUE"); d.setDistribueAt(LocalDateTime.now()); }
            else if ("read".equals(statut)) { d.setStatut("LU"); d.setLuAt(LocalDateTime.now()); }
            else if ("failed".equals(statut)) { d.setStatut("ECHOUE"); }
            em.merge(d);
        }
    }

    /** Automatisations basées sur des mots-clés (section 22 de la spec). */
    private void appliquerAutomatisations(Conversation conv, String texte) {
        if (texte == null) return;
        String t = texte.trim().toUpperCase();
        if (t.equals("STOP") && conv.getContactId() != null) {
            contactService.definirDesabonnement(conv.getContactId(), true);
        }
    }
}
