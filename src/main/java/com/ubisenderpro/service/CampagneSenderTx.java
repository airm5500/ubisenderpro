package com.ubisenderpro.service;

import com.ubisenderpro.entity.Campagne;
import com.ubisenderpro.entity.CampagneDestinataire;
import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.entity.WhatsappAccount;
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
     * de la campagne, le tout dans une transaction dédiée.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void envoyer(Long destinataireId, Long accountId, Long modeleId) {
        CampagneDestinataire d = em.find(CampagneDestinataire.class, destinataireId);
        if (d == null || !"EN_ATTENTE".equals(d.getStatut())) return;

        WhatsappAccount account = em.find(WhatsappAccount.class, accountId);
        ModeleMessage modele = em.find(ModeleMessage.class, modeleId);
        if (account == null || modele == null) return;

        WhatsappCloudClient client = new WhatsappCloudClient(account);
        WhatsappCloudClient.SendResult res = client.envoyerModele(
                d.getNumeroWhatsapp(),
                modele.getNomModeleWhatsapp() != null ? modele.getNomModeleWhatsapp() : modele.getNom(),
                modele.getLangue(),
                Collections.singletonList(d.getNomContact() == null ? "" : d.getNomContact()));

        Campagne c = em.find(Campagne.class, d.getCampagneId());
        if (res.success) {
            d.setStatut("ENVOYE");
            d.setWaMessageId(res.waMessageId);
            d.setEnvoyeAt(LocalDateTime.now());
            if (c != null) c.setNbEnvoyes(c.getNbEnvoyes() + 1);
        } else {
            d.setStatut("ECHOUE");
            d.setErreur(res.erreur);
            if (c != null) c.setNbEchoues(c.getNbEchoues() + 1);
        }
        em.merge(d);
        if (c != null) em.merge(c);
    }
}
