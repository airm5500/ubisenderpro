package com.ubisenderpro.service;

import com.ubisenderpro.entity.SegmentationClient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

@Stateless
public class SegmentationService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<SegmentationClient> lister() {
        return em.createQuery(
                "SELECT s FROM SegmentationClient s ORDER BY s.ordreAffichage, s.libelle",
                SegmentationClient.class).getResultList();
    }

    /**
     * Résout une segmentation depuis un libellé importé, en normalisant la casse
     * et les accents pour éviter les variantes (section 9.2 de la spec).
     * Crée la segmentation si elle n'existe pas et que la création est autorisée.
     */
    public Optional<SegmentationClient> resoudre(String libelle, boolean creerSiAbsente) {
        if (libelle == null || libelle.trim().isEmpty()) {
            return Optional.empty();
        }
        String cle = normaliser(libelle);

        List<SegmentationClient> all = lister();
        for (SegmentationClient s : all) {
            if (normaliser(s.getLibelle()).equals(cle) || normaliser(s.getCode()).equals(cle)) {
                return Optional.of(s);
            }
        }

        if (!creerSiAbsente) {
            return Optional.empty();
        }

        SegmentationClient nouvelle = new SegmentationClient();
        nouvelle.setCode(cle.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        nouvelle.setLibelle(libelle.trim());
        nouvelle.setOrdreAffichage(99);
        em.persist(nouvelle);
        return Optional.of(nouvelle);
    }

    private String normaliser(String valeur) {
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccent.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
