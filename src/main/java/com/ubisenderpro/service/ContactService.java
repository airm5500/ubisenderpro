package com.ubisenderpro.service;

import com.ubisenderpro.dto.PageResult;
import com.ubisenderpro.entity.ClientContact;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Stateless
public class ContactService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /**
     * Contacts disposant d'un numéro WhatsApp (non désabonnés), pour la sélection
     * de destinataires. Filtrable par segmentation du client et par recherche.
     */
    public List<Map<String, Object>> pourSelection(Long segmentationId, String q, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT ct.numeroWhatsapp, ct.nomComplet, cl.nomCompte FROM ClientContact ct, Client cl " +
                "WHERE ct.clientId = cl.id AND ct.numeroWhatsapp IS NOT NULL AND ct.numeroWhatsapp <> '' " +
                "AND ct.desabonne = false ");
        boolean hasSeg = segmentationId != null;
        boolean hasQ = q != null && !q.trim().isEmpty();
        if (hasSeg) { jpql.append("AND cl.segmentationId = :seg "); }
        if (hasQ) { jpql.append("AND (LOWER(ct.nomComplet) LIKE :q OR LOWER(cl.nomCompte) LIKE :q) "); }
        jpql.append("ORDER BY cl.nomCompte, ct.nomComplet");

        Query query = em.createQuery(jpql.toString());
        if (hasSeg) { query.setParameter("seg", segmentationId); }
        if (hasQ) { query.setParameter("q", "%" + q.trim().toLowerCase() + "%"); }
        query.setFirstResult(Math.max(0, offset)).setMaxResults(limit <= 0 ? 500 : limit);

        List<Object[]> rows = query.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("numero", r[0]);
            m.put("nom", r[1]);
            m.put("client", r[2]);
            out.add(m);
        }
        return out;
    }

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
