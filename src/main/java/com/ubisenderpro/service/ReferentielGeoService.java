package com.ubisenderpro.service;

import com.ubisenderpro.entity.ReferentielGeo;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Gestion des référentiels géographiques (pays, régions, villes, communes,
 * agences). Fournit la liste pour les listes déroulantes et l'« assurance
 * d'existence » utilisée à la création client et à l'import (auto-création
 * dédupliquée), afin d'éviter les valeurs libres divergentes.
 */
@Stateless
public class ReferentielGeoService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /** Types de référentiel autorisés. */
    public static final List<String> TYPES =
            Arrays.asList("PAYS", "REGION", "VILLE", "COMMUNE", "AGENCE");

    /** Préfixe de code généré par type. */
    private static String prefixe(String type) {
        switch (type) {
            case "PAYS": return "PAYS";
            case "REGION": return "REG";
            case "VILLE": return "VILLE";
            case "COMMUNE": return "COM";
            case "AGENCE": return "AG";
            default: return "GEO";
        }
    }

    /** Normalise et valide le type (lève si inconnu). */
    public static String typeValide(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!TYPES.contains(t)) {
            throw new ValidationException("type", "Type de référentiel inconnu : " + type);
        }
        return t;
    }

    public List<ReferentielGeo> lister(String type) {
        return em.createQuery(
                "SELECT r FROM ReferentielGeo r WHERE r.type = :t AND r.actif = true ORDER BY r.libelle",
                ReferentielGeo.class).setParameter("t", typeValide(type)).getResultList();
    }

    /**
     * Garantit qu'une valeur existe dans le référentiel (création si absente,
     * dédupliquée sans tenir compte de la casse/espaces) et renvoie le libellé
     * canonique à stocker. Renvoie {@code null} pour une valeur vide.
     */
    public String assurer(String type, String libelle) {
        if (libelle == null || libelle.trim().isEmpty()) { return null; }
        String t = typeValide(type);
        String lib = libelle.trim();
        List<ReferentielGeo> existant = em.createQuery(
                "SELECT r FROM ReferentielGeo r WHERE r.type = :t AND LOWER(r.libelle) = :l",
                ReferentielGeo.class)
                .setParameter("t", t).setParameter("l", lib.toLowerCase())
                .setMaxResults(1).getResultList();
        if (!existant.isEmpty()) { return existant.get(0).getLibelle(); }
        ReferentielGeo r = new ReferentielGeo();
        r.setType(t);
        r.setLibelle(lib);
        r.setCode(Codes.generer(prefixe(t), c -> codeExiste(t, c)));
        r.setActif(true);
        em.persist(r);
        return lib;
    }

    private boolean codeExiste(String type, String code) {
        return !em.createQuery(
                "SELECT r FROM ReferentielGeo r WHERE r.type = :t AND r.code = :c", ReferentielGeo.class)
                .setParameter("t", type).setParameter("c", code).setMaxResults(1).getResultList().isEmpty();
    }

    public ReferentielGeo creer(String type, ReferentielGeo r) {
        String t = typeValide(type);
        if (r.getLibelle() == null || r.getLibelle().trim().isEmpty()) {
            throw new ValidationException("libelle", "Le libellé est obligatoire.");
        }
        String lib = r.getLibelle().trim();
        if (!em.createQuery(
                "SELECT r FROM ReferentielGeo r WHERE r.type = :t AND LOWER(r.libelle) = :l", ReferentielGeo.class)
                .setParameter("t", t).setParameter("l", lib.toLowerCase()).setMaxResults(1).getResultList().isEmpty()) {
            throw new ValidationException("libelle", "« " + lib + " » existe déjà dans ce référentiel.");
        }
        r.setType(t);
        r.setLibelle(lib);
        if (r.getCode() == null || r.getCode().trim().isEmpty()) {
            r.setCode(Codes.generer(prefixe(t), c -> codeExiste(t, c)));
        }
        r.setActif(true);
        em.persist(r);
        return r;
    }

    public ReferentielGeo modifier(Long id, ReferentielGeo data) {
        ReferentielGeo ex = em.find(ReferentielGeo.class, id);
        if (ex == null) { throw new IllegalArgumentException("Valeur de référentiel introuvable"); }
        if (data.getLibelle() != null && !data.getLibelle().trim().isEmpty()) {
            ex.setLibelle(data.getLibelle().trim());
        }
        if (data.getCode() != null && !data.getCode().trim().isEmpty()) {
            ex.setCode(data.getCode().trim());
        }
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void definirActif(Long id, boolean actif) {
        ReferentielGeo ex = em.find(ReferentielGeo.class, id);
        if (ex != null) { ex.setActif(actif); ex.setUpdatedAt(LocalDateTime.now()); em.merge(ex); }
    }
}
