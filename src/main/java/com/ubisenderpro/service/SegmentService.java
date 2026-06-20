package com.ubisenderpro.service;

import com.ubisenderpro.entity.ClientContact;
import com.ubisenderpro.entity.Segment;
import com.ubisenderpro.entity.SegmentFiltre;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Segments dynamiques (section 18 de la spec). Évalue les filtres pour produire
 * la liste des contacts ciblés. Critères supportés en Phase 2 : region, agence,
 * ville, commune, consentement, desabonne, whatsapp_present, statut_client.
 */
@Stateless
public class SegmentService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<Segment> lister() {
        return em.createQuery("SELECT s FROM Segment s ORDER BY s.nom", Segment.class).getResultList();
    }

    public Optional<Segment> parId(Long id) { return Optional.ofNullable(em.find(Segment.class, id)); }

    public Segment creer(Segment s) { em.persist(s); return s; }

    public SegmentFiltre ajouterFiltre(SegmentFiltre f) { em.persist(f); return f; }

    public List<SegmentFiltre> filtres(Long segmentId) {
        return em.createQuery("SELECT f FROM SegmentFiltre f WHERE f.segmentId = :s", SegmentFiltre.class)
                .setParameter("s", segmentId).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<ClientContact> evaluer(Long segmentId) {
        Segment segment = em.find(Segment.class, segmentId);
        if (segment == null) return new ArrayList<>();
        List<SegmentFiltre> filtres = filtres(segmentId);

        StringBuilder jpql = new StringBuilder(
                "SELECT ct FROM ClientContact ct, Client c WHERE ct.clientId = c.id");
        String liaison = "OU".equalsIgnoreCase(segment.getLogique()) ? " OR " : " AND ";
        List<Object[]> params = new ArrayList<>();

        if (!filtres.isEmpty()) {
            jpql.append(" AND (");
            int i = 0;
            for (SegmentFiltre f : filtres) {
                if (i > 0) jpql.append(liaison);
                String p = "p" + i;
                switch (f.getCritere()) {
                    case "region":      jpql.append("c.region = :").append(p); params.add(new Object[]{p, f.getValeur()}); break;
                    case "agence":      jpql.append("c.agence = :").append(p); params.add(new Object[]{p, f.getValeur()}); break;
                    case "ville":       jpql.append("c.ville = :").append(p); params.add(new Object[]{p, f.getValeur()}); break;
                    case "commune":     jpql.append("c.commune = :").append(p); params.add(new Object[]{p, f.getValeur()}); break;
                    case "statut_client": jpql.append("c.statut = :").append(p); params.add(new Object[]{p, f.getValeur()}); break;
                    case "segmentation": jpql.append("c.segmentationId = :").append(p); params.add(new Object[]{p, Long.valueOf(f.getValeur())}); break;
                    case "consentement": jpql.append("ct.consentementWhatsapp = true"); break;
                    case "desabonne":    jpql.append("ct.desabonne = false"); break;
                    case "whatsapp_present": jpql.append("ct.numeroWhatsapp IS NOT NULL AND ct.numeroWhatsapp <> ''"); break;
                    default:             jpql.append("1=1");
                }
                i++;
            }
            jpql.append(")");
        }

        Query q = em.createQuery(jpql.toString());
        for (Object[] p : params) q.setParameter((String) p[0], p[1]);
        return q.getResultList();
    }
}
