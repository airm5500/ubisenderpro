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

    public Optional<SegmentationClient> parId(Long id) {
        return Optional.ofNullable(em.find(SegmentationClient.class, id));
    }

    public SegmentationClient creer(SegmentationClient s) {
        valider(s, null);
        em.persist(s);
        return s;
    }

    public SegmentationClient modifier(Long id, SegmentationClient s) {
        SegmentationClient ex = em.find(SegmentationClient.class, id);
        if (ex == null) { throw new IllegalArgumentException("Segmentation introuvable"); }
        valider(s, id);
        ex.setCode(s.getCode());
        ex.setLibelle(s.getLibelle());
        ex.setDescription(s.getDescription());
        ex.setOrdreAffichage(s.getOrdreAffichage());
        ex.setActif(s.isActif());
        ex.setUpdatedAt(java.time.LocalDateTime.now());
        return em.merge(ex);
    }

    /** Supprime une segmentation si aucun client n'y est rattaché. */
    public void supprimer(Long id) {
        long lies = em.createQuery("SELECT COUNT(c) FROM Client c WHERE c.segmentationId = :s", Long.class)
                .setParameter("s", id).getSingleResult();
        if (lies > 0) {
            throw new IllegalArgumentException(lies + " client(s) utilisent cette segmentation : "
                    + "réaffectez-les avant de la supprimer.");
        }
        parId(id).ifPresent(em::remove);
    }

    private void valider(SegmentationClient s, Long idExclu) {
        if (s.getCode() == null || s.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code de la segmentation est obligatoire");
        }
        if (s.getLibelle() == null || s.getLibelle().trim().isEmpty()) {
            throw new IllegalArgumentException("Le libellé de la segmentation est obligatoire");
        }
        List<SegmentationClient> existants = em.createQuery(
                "SELECT s FROM SegmentationClient s WHERE s.code = :c OR s.libelle = :l", SegmentationClient.class)
                .setParameter("c", s.getCode().trim()).setParameter("l", s.getLibelle().trim()).getResultList();
        for (SegmentationClient e : existants) {
            if (idExclu == null || !e.getId().equals(idExclu)) {
                throw new IllegalArgumentException("Une segmentation avec ce code ou ce libellé existe déjà");
            }
        }
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
