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
            Arrays.asList("PAYS", "REGION", "VILLE", "COMMUNE", "AGENCE", "TOURNEE");

    /** Préfixe de code généré par type. */
    private static String prefixe(String type) {
        switch (type) {
            case "PAYS": return "PAYS";
            case "REGION": return "REG";
            case "VILLE": return "VILLE";
            case "COMMUNE": return "COM";
            case "AGENCE": return "AG";
            case "TOURNEE": return "TOUR";
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

    /**
     * Importe des valeurs depuis un contenu texte (une ligne par valeur).
     * Colonnes acceptées : {@code id,code,nom} ou {@code code,nom} ou {@code nom}
     * (séparateur , ou ;). Dédupliqué par libellé. Renvoie le nombre créé.
     */
    public int importer(String type, String contenu) {
        return importerRapport(type, contenu).getOrDefault("crees", 0);
    }

    /**
     * Import détaillé pour l'assistant : renvoie un rapport
     * {@code {lues, crees, ignores}} (lignes lues hors en-tête, valeurs créées,
     * doublons/lignes vides ignorés).
     */
    public java.util.Map<String, Integer> importerRapport(String type, String contenu) {
        String t = typeValide(type);
        java.util.Map<String, Integer> r = new java.util.LinkedHashMap<>();
        r.put("lues", 0); r.put("crees", 0); r.put("ignores", 0);
        if (contenu == null || contenu.trim().isEmpty()) { return r; }
        int lues = 0, crees = 0, ignores = 0;
        boolean premiere = true;
        for (String ligne : contenu.split("\\r?\\n")) {
            if (ligne == null || ligne.trim().isEmpty()) { continue; }
            String[] cols = ligne.split("[;,\\t]");
            for (int i = 0; i < cols.length; i++) { cols[i] = cols[i].trim(); }
            if (premiere) {
                premiere = false;
                if (estEntete(cols)) { continue; }
            }
            // Format attendu : code;libellé (sans id). 1 seule colonne = libellé seul.
            String code = null, nom;
            if (cols.length >= 2) { code = cols[0]; nom = cols[1]; }
            else { nom = cols[0]; }
            if (nom == null || nom.isEmpty()) { ignores++; continue; }
            lues++;
            if (importerUn(t, code, nom)) { crees++; } else { ignores++; }
        }
        r.put("lues", lues); r.put("crees", crees); r.put("ignores", ignores);
        return r;
    }

    private boolean estEntete(String[] cols) {
        for (String c : cols) {
            String v = c.toLowerCase();
            if (v.equals("id") || v.equals("code") || v.equals("nom")
                    || v.equals("libelle") || v.equals("libellé")) { return true; }
        }
        return false;
    }

    /**
     * Import via l'assistant à mapping de colonnes (comme le Catalogue) : fichier
     * CSV/Excel + correspondance {code, libelle}. Renvoie un rapport standard.
     */
    public com.ubisenderpro.dto.ImportReport importerAssistant(String type, com.ubisenderpro.dto.ImportClientRequest req) {
        String t = typeValide(type);
        com.ubisenderpro.dto.ImportReport rapport = new com.ubisenderpro.dto.ImportReport();
        rapport.setTypeImport("REFERENTIEL_" + t);
        java.util.List<java.util.Map<String, String>> lignes;
        try {
            byte[] contenu = java.util.Base64.getDecoder().decode(req.getFichierBase64());
            char sep = req.getSeparateur() != null && !req.getSeparateur().isEmpty() ? req.getSeparateur().charAt(0) : ';';
            lignes = com.ubisenderpro.importer.FileParser.parse(contenu, req.getNomFichier(), sep);
        } catch (Exception e) {
            rapport.ajouterErreur(0, "Lecture du fichier impossible : " + e.getMessage());
            return rapport;
        }
        rapport.setLignesLues(lignes.size());
        String colCode = req.getMapping().getOrDefault("code", "code");
        String colLib = req.getMapping().getOrDefault("libelle", "libelle");
        int crees = 0, ignores = 0, rejets = 0, num = 1;
        for (java.util.Map<String, String> ligne : lignes) {
            num++;
            String nom = valeur(ligne, colLib);
            String code = valeur(ligne, colCode);
            if (nom == null || nom.isEmpty()) { rejets++; rapport.ajouterErreur(num, "Libellé manquant"); continue; }
            if (req.isSimulation()) { crees++; continue; }
            if (importerUn(t, code, nom)) { crees++; } else { ignores++; }
        }
        rapport.setComptesCrees(crees);
        rapport.setLignesIgnorees(ignores);
        rapport.setLignesRejetees(rejets);
        return rapport;
    }

    private String valeur(java.util.Map<String, String> ligne, String colonne) {
        if (colonne == null) { return null; }
        String v = ligne.get(colonne);
        return v == null ? null : v.trim();
    }

    private boolean importerUn(String type, String code, String nom) {
        boolean existe = !em.createQuery(
                "SELECT r FROM ReferentielGeo r WHERE r.type = :t AND LOWER(r.libelle) = :l", ReferentielGeo.class)
                .setParameter("t", type).setParameter("l", nom.toLowerCase())
                .setMaxResults(1).getResultList().isEmpty();
        if (existe) { return false; }
        ReferentielGeo r = new ReferentielGeo();
        r.setType(type);
        r.setLibelle(nom);
        String c = (code == null || code.isEmpty() || codeExiste(type, code))
                ? Codes.generer(prefixe(type), x -> codeExiste(type, x)) : code;
        r.setCode(c);
        r.setActif(true);
        em.persist(r);
        return true;
    }
}
