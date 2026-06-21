package com.ubisenderpro.service;

import com.ubisenderpro.entity.*;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Création, ciblage et lancement des campagnes (section 21 de la spec).
 * Le lancement envoie un message modèle à chaque destinataire valide via la
 * Cloud API, exclut les désabonnés et les numéros invalides, et suit les statuts.
 */
@Stateless
public class CampagneService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private SegmentService segmentService;
    @EJB
    private ListeService listeService;

    public List<Campagne> lister() {
        return em.createQuery("SELECT c FROM Campagne c ORDER BY c.createdAt DESC", Campagne.class).getResultList();
    }

    public Optional<Campagne> parId(Long id) { return Optional.ofNullable(em.find(Campagne.class, id)); }

    public Campagne creer(Campagne c) { em.persist(c); return c; }
    public Campagne modifier(Campagne c) {
        Campagne ex = em.find(Campagne.class, c.getId());
        if (ex != null) {
            c.setCreatedAt(ex.getCreatedAt());
            if (c.getCreePar() == null) { c.setCreePar(ex.getCreePar()); }
        }
        return em.merge(c);
    }

    /**
     * Construit la liste des destinataires depuis la liste statique et/ou le segment
     * dynamique attachés à la campagne, en supprimant les doublons et les désabonnés.
     */
    public int construireDestinataires(Long campagneId) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c == null) return 0;

        // Purge des destinataires précédents.
        em.createQuery("DELETE FROM CampagneDestinataire d WHERE d.campagneId = :c")
                .setParameter("c", campagneId).executeUpdate();

        List<ClientContact> contacts = new ArrayList<>();
        if (c.getListeId() != null) contacts.addAll(listeService.contacts(c.getListeId()));
        if (c.getSegmentId() != null) contacts.addAll(segmentService.evaluer(c.getSegmentId()));
        if (c.getSegmentationId() != null) contacts.addAll(contactsParSegmentation(c.getSegmentationId()));

        java.util.Set<String> vus = new java.util.HashSet<>();
        int total = 0;
        for (ClientContact ct : contacts) {
            String numero = ct.getNumeroWhatsapp();
            CampagneDestinataire d = new CampagneDestinataire();
            d.setCampagneId(campagneId);
            d.setContactId(ct.getId());
            d.setNumeroWhatsapp(numero);
            d.setNomContact(ct.getNomComplet());

            if (ct.isDesabonne()) d.setStatut("DESABONNE");
            else if (numero == null || numero.isEmpty()) d.setStatut("NUMERO_INVALIDE");
            else if (!vus.add(numero)) continue; // doublon
            else d.setStatut("EN_ATTENTE");

            em.persist(d);
            total++;
        }
        c.setNbDestinataires(total);
        em.merge(c);
        return total;
    }

    /** Contacts WhatsApp des clients d'une segmentation donnée. */
    private List<ClientContact> contactsParSegmentation(Long segmentationId) {
        return em.createQuery(
                "SELECT ct FROM ClientContact ct, Client cl WHERE ct.clientId = cl.id " +
                "AND cl.segmentationId = :seg AND ct.numeroWhatsapp IS NOT NULL AND ct.numeroWhatsapp <> ''",
                ClientContact.class)
                .setParameter("seg", segmentationId).getResultList();
    }

    /**
     * Valide qu'une campagne est lançable (compte WhatsApp + modèle définis).
     * L'envoi effectif est délégué à {@link CampagneSenderAsync} en arrière-plan.
     */
    public void verifierLancable(Long campagneId) {
        Campagne c = em.find(Campagne.class, campagneId);
        if (c == null) throw new IllegalArgumentException("Campagne introuvable");
        if (c.getWhatsappAccountId() == null) throw new IllegalArgumentException("Compte WhatsApp non défini");
        if (c.getModeleId() == null) throw new IllegalArgumentException("Modèle de message non défini");
    }

    public void changerStatut(Long campagneId, String statut) {
        parId(campagneId).ifPresent(c -> { c.setStatut(statut); em.merge(c); });
    }
}
