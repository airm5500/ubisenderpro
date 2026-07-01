package com.ubisenderpro.service;

import com.ubisenderpro.entity.RecReferentiel;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Référentiels paramétrables du module Recouvrement : segments commerciaux,
 * profils de paiement, statuts de recouvrement. Liste + CRUD + import CSV,
 * sur le modèle des référentiels géographiques.
 */
@Stateless
public class RecReferentielService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public static final List<String> TYPES =
            Arrays.asList("SEGMENT_COMMERCIAL", "PROFIL_PAIEMENT", "STATUT_RECOUVREMENT");

    private static String prefixe(String type) {
        switch (type) {
            case "SEGMENT_COMMERCIAL": return "SEG";
            case "PROFIL_PAIEMENT": return "PROF";
            case "STATUT_RECOUVREMENT": return "STAT";
            default: return "REC";
        }
    }

    public static String typeValide(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!TYPES.contains(t)) {
            throw new ValidationException("type", "Type de référentiel inconnu : " + type);
        }
        return t;
    }

    public List<RecReferentiel> lister(String type) {
        return em.createQuery(
                "SELECT r FROM RecReferentiel r WHERE r.type = :t AND r.actif = true ORDER BY r.libelle",
                RecReferentiel.class).setParameter("t", typeValide(type)).getResultList();
    }

    private boolean codeExiste(String type, String code) {
        return !em.createQuery(
                "SELECT r FROM RecReferentiel r WHERE r.type = :t AND r.code = :c", RecReferentiel.class)
                .setParameter("t", type).setParameter("c", code).setMaxResults(1).getResultList().isEmpty();
    }

    private boolean libelleExiste(String type, String libelle) {
        return !em.createQuery(
                "SELECT r FROM RecReferentiel r WHERE r.type = :t AND LOWER(r.libelle) = :l", RecReferentiel.class)
                .setParameter("t", type).setParameter("l", libelle.toLowerCase())
                .setMaxResults(1).getResultList().isEmpty();
    }

    public RecReferentiel creer(String type, RecReferentiel r) {
        String t = typeValide(type);
        if (r.getLibelle() == null || r.getLibelle().trim().isEmpty()) {
            throw new ValidationException("libelle", "Le libellé est obligatoire.");
        }
        String lib = r.getLibelle().trim();
        if (libelleExiste(t, lib)) {
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

    public RecReferentiel modifier(Long id, RecReferentiel data) {
        RecReferentiel ex = em.find(RecReferentiel.class, id);
        if (ex == null) { throw new IllegalArgumentException("Valeur de référentiel introuvable"); }
        if (data.getLibelle() != null && !data.getLibelle().trim().isEmpty()) { ex.setLibelle(data.getLibelle().trim()); }
        if (data.getCode() != null && !data.getCode().trim().isEmpty()) { ex.setCode(data.getCode().trim()); }
        ex.setUpdatedAt(LocalDateTime.now());
        return em.merge(ex);
    }

    public void definirActif(Long id, boolean actif) {
        RecReferentiel ex = em.find(RecReferentiel.class, id);
        if (ex != null) { ex.setActif(actif); ex.setUpdatedAt(LocalDateTime.now()); em.merge(ex); }
    }

    /** Import CSV : {@code id,code,nom} ou {@code code,nom} ou {@code nom} (déduplication par libellé). */
    public int importer(String type, String contenu) {
        String t = typeValide(type);
        if (contenu == null || contenu.trim().isEmpty()) { return 0; }
        int crees = 0;
        boolean premiere = true;
        for (String ligne : contenu.split("\\r?\\n")) {
            if (ligne == null || ligne.trim().isEmpty()) { continue; }
            String[] cols = ligne.split("[;,\\t]");
            for (int i = 0; i < cols.length; i++) { cols[i] = cols[i].trim(); }
            if (premiere) {
                premiere = false;
                boolean entete = false;
                for (String c : cols) {
                    String v = c.toLowerCase();
                    if (v.equals("id") || v.equals("code") || v.equals("nom") || v.equals("libelle")) { entete = true; }
                }
                if (entete) { continue; }
            }
            // Format attendu : code;libellé (sans id). 1 seule colonne = libellé seul.
            String code = null, nom;
            if (cols.length >= 2) { code = cols[0]; nom = cols[1]; }
            else { nom = cols[0]; }
            if (creerUn(t, code, nom)) { crees++; }
        }
        return crees;
    }

    /** Crée une valeur de référentiel (code auto si absent/en conflit). Faux si doublon/vide. */
    private boolean creerUn(String type, String code, String nom) {
        if (nom == null || nom.isEmpty() || libelleExiste(type, nom)) { return false; }
        RecReferentiel r = new RecReferentiel();
        r.setType(type);
        r.setLibelle(nom);
        r.setCode((code == null || code.isEmpty() || codeExiste(type, code))
                ? Codes.generer(prefixe(type), c -> codeExiste(type, c)) : code);
        r.setActif(true);
        em.persist(r);
        return true;
    }

    /** Import via l'assistant à mapping de colonnes (fichier CSV/Excel + correspondance {code, libelle}). */
    public com.ubisenderpro.dto.ImportReport importerAssistant(String type, com.ubisenderpro.dto.ImportClientRequest req) {
        String t = typeValide(type);
        com.ubisenderpro.dto.ImportReport rapport = new com.ubisenderpro.dto.ImportReport();
        rapport.setTypeImport("REF_RECOUVREMENT_" + t);
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
            String nom = ligne.get(colLib) == null ? null : ligne.get(colLib).trim();
            String code = ligne.get(colCode) == null ? null : ligne.get(colCode).trim();
            if (nom == null || nom.isEmpty()) { rejets++; rapport.ajouterErreur(num, "Libellé manquant"); continue; }
            if (req.isSimulation()) { crees++; continue; }
            if (creerUn(t, code, nom)) { crees++; } else { ignores++; }
        }
        rapport.setComptesCrees(crees);
        rapport.setLignesIgnorees(ignores);
        rapport.setLignesRejetees(rejets);
        return rapport;
    }
}
