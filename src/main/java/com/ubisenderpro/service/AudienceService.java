package com.ubisenderpro.service;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Résolution des audiences logiques (§16) en segmentations clients (par libellé/code).
 * DIAMOND / PLATINIUM correspondent à des segmentations ; DIAMOND_ET_PLATINIUM
 * combine les deux ; TOUS_LES_SEGMENTS cible tout le monde (aucune segmentation).
 */
@Stateless
public class AudienceService {

    public static final String TOUS = "TOUS_LES_SEGMENTS";

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /** IDs de segmentations à cibler pour une audience (vide = tous / non applicable). */
    public List<Long> segmentationIds(String audience) {
        if (audience == null) { return Collections.emptyList(); }
        switch (audience) {
            case "DIAMOND": return parLibelle("DIAMOND");
            case "PLATINIUM": return parLibelle("PLATIN");
            case "DIAMOND_ET_PLATINIUM": {
                List<Long> l = new ArrayList<>(parLibelle("DIAMOND"));
                for (Long id : parLibelle("PLATIN")) { if (!l.contains(id)) { l.add(id); } }
                return l;
            }
            default: return Collections.emptyList();
        }
    }

    /** Représentation CSV des segmentations résolues (null si aucune). */
    public String segmentationIdsCsv(String audience) {
        List<Long> ids = segmentationIds(audience);
        if (ids.isEmpty()) { return null; }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) { if (sb.length() > 0) { sb.append(','); } sb.append(id); }
        return sb.toString();
    }

    private List<Long> parLibelle(String motif) {
        return em.createQuery(
                "SELECT s.id FROM SegmentationClient s WHERE s.actif = true " +
                "AND (UPPER(s.libelle) LIKE :m OR UPPER(s.code) LIKE :m)", Long.class)
                .setParameter("m", "%" + motif.toUpperCase() + "%").getResultList();
    }
}
