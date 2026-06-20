package com.ubisenderpro.service;

import com.ubisenderpro.entity.ImportMapping;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion des modèles de correspondance d'import (usp_import_mapping).
 * Permet de réimporter un fichier sans reconfigurer le mapping (section 25.1).
 */
@Stateless
public class ImportMappingService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<ImportMapping> lister(String typeImport) {
        if (typeImport == null || typeImport.isEmpty()) {
            return em.createQuery("SELECT m FROM ImportMapping m WHERE m.actif = true ORDER BY m.nom",
                    ImportMapping.class).getResultList();
        }
        return em.createQuery(
                "SELECT m FROM ImportMapping m WHERE m.actif = true AND m.typeImport = :t ORDER BY m.nom",
                ImportMapping.class).setParameter("t", typeImport).getResultList();
    }

    public Optional<ImportMapping> parId(Long id) { return Optional.ofNullable(em.find(ImportMapping.class, id)); }

    /** Enregistre ou met à jour un modèle de mapping (clé : nom + type). */
    public ImportMapping enregistrer(ImportMapping m) {
        List<ImportMapping> existant = em.createQuery(
                "SELECT m FROM ImportMapping m WHERE m.nom = :n AND m.typeImport = :t", ImportMapping.class)
                .setParameter("n", m.getNom()).setParameter("t", m.getTypeImport())
                .setMaxResults(1).getResultList();
        if (!existant.isEmpty()) {
            ImportMapping e = existant.get(0);
            e.setMappingJson(m.getMappingJson());
            e.setSeparateur(m.getSeparateur());
            e.setActif(true);
            e.setUpdatedAt(LocalDateTime.now());
            return em.merge(e);
        }
        em.persist(m);
        return m;
    }

    public void supprimer(Long id) {
        parId(id).ifPresent(m -> { m.setActif(false); em.merge(m); });
    }
}
