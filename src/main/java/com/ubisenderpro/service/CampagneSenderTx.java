package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.CampagneDestinataire;
import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.entity.WhatsappAccount;
import com.ubisenderpro.whatsapp.WaWebClient;
import com.ubisenderpro.whatsapp.WhatsappCloudClient;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Étapes transactionnelles unitaires du lancement de campagne. Chaque envoi est
 * committé dans sa propre transaction (REQUIRES_NEW) pour éviter une transaction
 * longue couvrant l'ensemble du lot.
 */
@Stateless
public class CampagneSenderTx {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void marquerStatut(Long campagneId, String statut) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c != null) { c.setStatut(statut); em.merge(c); }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<Long> idsEnAttente(Long campagneId) {
        return em.createQuery(
                "SELECT d.id FROM CampagneDestinataire d WHERE d.campagneId = :c AND d.statut = 'EN_ATTENTE'",
                Long.class).setParameter("c", campagneId).getResultList();
    }

    /**
     * Envoie le modèle à un destinataire et met à jour son statut + les compteurs
     * de la campagne, le tout dans une transaction dédiée. Le canal d'envoi
     * (Cloud API officielle ou WhatsApp Web) est déterminé par la campagne.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void envoyer(Long destinataireId, Long accountId, Long modeleId) {
        CampagneDestinataire d = em.find(CampagneDestinataire.class, destinataireId);
        if (d == null || !"EN_ATTENTE".equals(d.getStatut())) return;

        ModeleMessage modele = em.find(ModeleMessage.class, modeleId);
        if (modele == null) return;

        Campagne c = em.find(Campagne.class, d.getCampagneId());
        d.setTentatives(d.getTentatives() + 1);

        boolean success;
        String waMessageId = null;
        String erreur;

        if (c != null && "WEB".equalsIgnoreCase(c.getCanal())) {
            // Canal non officiel : envoi du corps du modèle (placeholder {{1}} = nom du contact).
            if (c.getWaWebSessionId() == null || c.getWaWebSessionId().isEmpty()) {
                success = false; erreur = "Session WhatsApp Web non définie pour la campagne";
            } else {
                WaWebClient web = new WaWebClient();
                WaWebClient.SendResult res = web.sendText(
                        c.getWaWebSessionId(), d.getNumeroWhatsapp(), corpsPersonnalise(modele, d));
                success = res.success; waMessageId = res.id; erreur = res.erreur;
            }
        } else {
            WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
            if (account == null) {
                success = false; erreur = "Compte WhatsApp (Cloud API) introuvable";
            } else {
                WhatsappCloudClient client = new WhatsappCloudClient(account);
                WhatsappCloudClient.SendResult res = client.envoyerModele(
                        d.getNumeroWhatsapp(),
                        modele.getNomModeleWhatsapp() != null ? modele.getNomModeleWhatsapp() : modele.getNom(),
                        modele.getLangue(),
                        Collections.singletonList(d.getNomContact() == null ? "" : d.getNomContact()),
                        modele.getEnteteMediaType(), modele.getEnteteMediaUrl());
                success = res.success; waMessageId = res.waMessageId; erreur = res.erreur;
            }
        }

        if (success) {
            d.setStatut("ENVOYE");
            d.setWaMessageId(waMessageId);
            d.setErreur(null);
            d.setEnvoyeAt(LocalDateTime.now());
            if (c != null) c.setNbEnvoyes(c.getNbEnvoyes() + 1);
        } else {
            d.setStatut("ECHOUE");
            d.setErreur(erreur);
            if (c != null) c.setNbEchoues(c.getNbEchoues() + 1);
        }
        em.merge(d);
        if (c != null) em.merge(c);
    }

    /** Corps du modèle avec le placeholder {{1}} remplacé par le nom du contact (canal WEB). */
    private String corpsPersonnalise(ModeleMessage modele, CampagneDestinataire d) {
        String corps = modele.getCorps() != null ? modele.getCorps() : modele.getNom();
        String nom = d.getNomContact() == null ? "" : d.getNomContact();
        return corps.replace("{{1}}", nom);
    }
}
