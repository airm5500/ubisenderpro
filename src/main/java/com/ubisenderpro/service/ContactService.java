package com.ubisenderpro.service;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.ClientContact;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Stateless
public class ContactService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public PageResult<ClientContact> parClient(Long clientId, int offset, int limit) {
        List<ClientContact> data = em.createQuery(
                "SELECT c FROM ClientContact c WHERE c.clientId = :cid ORDER BY c.contactPrincipal DESC, c.nomComplet",
                ClientContact.class)
                .setParameter("cid", clientId)
                .setFirstResult(offset).setMaxResults(limit)
                .getResultList();
        long total = em.createQuery(
                "SELECT COUNT(c) FROM ClientContact c WHERE c.clientId = :cid", Long.class)
                .setParameter("cid", clientId)
                .getSingleResult();
        return new PageResult<>(data, total);
    }

    public Optional<ClientContact> parId(Long id) {
        return Optional.ofNullable(em.find(ClientContact.class, id));
    }

    /**
     * Recherche un contact existant pour éviter les doublons (section 25.3 de la spec) :
     * client + numéro WhatsApp, puis client + téléphone principal, puis client + nom complet.
     */
    public Optional<ClientContact> trouverDoublon(Long clientId, String whatsapp,
                                                  String telephone, String nomComplet) {
        if (whatsapp != null && !whatsapp.isEmpty()) {
            Optional<ClientContact> r = chercher(clientId, "numeroWhatsapp", whatsapp);
            if (r.isPresent()) return r;
        }
        if (telephone != null && !telephone.isEmpty()) {
            Optional<ClientContact> r = chercher(clientId, "telephonePrincipal", telephone);
            if (r.isPresent()) return r;
        }
        if (nomComplet != null && !nomComplet.isEmpty()) {
            return chercher(clientId, "nomComplet", nomComplet);
        }
        return Optional.empty();
    }

    private Optional<ClientContact> chercher(Long clientId, String champ, String valeur) {
        List<ClientContact> list = em.createQuery(
                "SELECT c FROM ClientContact c WHERE c.clientId = :cid AND c." + champ + " = :val",
                ClientContact.class)
                .setParameter("cid", clientId)
                .setParameter("val", valeur)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public ClientContact creer(ClientContact contact) {
        em.persist(contact);
        return contact;
    }

    public ClientContact modifier(ClientContact contact) {
        contact.setUpdatedAt(LocalDateTime.now());
        return em.merge(contact);
    }

    public void supprimer(Long id) {
        parId(id).ifPresent(em::remove);
    }

    public void definirDesabonnement(Long id, boolean desabonne) {
        parId(id).ifPresent(c -> {
            c.setDesabonne(desabonne);
            em.merge(c);
        });
    }
}
