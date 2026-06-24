package com.ubisenderpro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.entity.SegmentationClient;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Export/import des modèles de message au format Word (.docx) via Apache POI.
 * Le contenu lisible est accompagné d'une propriété de document structurée
 * (JSON) permettant un ré-import fidèle (round-trip).
 */
@Stateless
public class ModeleDocxService {

    /** Nom de la propriété de document portant le modèle sérialisé (JSON). */
    private static final String PROP = "ubisenderpro-modele";
    public static final String MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    /** Nom de fichier : nom_modele_segmentation_aaaammjj_hh_mm.docx (all_segmentation si aucune). */
    public String nomFichier(ModeleMessage m) {
        String seg = segmentationLibelle(m.getSegmentationId());
        String segPart = (seg == null || seg.isEmpty()) ? "all_segmentation" : assainir(seg);
        String dt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HH_mm"));
        return assainir(m.getNom()) + "_" + segPart + "_" + dt + ".docx";
    }

    /** Génère le .docx (contenu lisible + données JSON en propriété de document). */
    public byte[] exporter(ModeleMessage m) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            titre(doc, "Modèle de message — " + nvl(m.getNom()));

            String seg = segmentationLibelle(m.getSegmentationId());
            champ(doc, "Nom", m.getNom());
            champ(doc, "Type", m.getTypeModele());
            champ(doc, "Langue", m.getLangue());
            champ(doc, "Catégorie", m.getCategorie());
            champ(doc, "Segmentation", seg == null ? "Toutes les segmentations" : seg);
            champ(doc, "En-tête (texte)", m.getEnteteTexte());
            champ(doc, "En-tête média (type)", m.getEnteteMediaType());
            champ(doc, "En-tête média (URL)", m.getEnteteMediaUrl());
            champ(doc, "Pied de page", m.getPiedDePage());
            champ(doc, "Boutons (JSON)", m.getBoutonsJson());
            champ(doc, "Nom du modèle Meta", m.getNomModeleWhatsapp());
            champ(doc, "Approbation", m.getStatutApprobation());

            titre(doc, "Corps du message");
            corps(doc, nvl(m.getCorps()));

            // Données structurées pour le ré-import.
            POIXMLProperties.CustomProperties cp = doc.getProperties().getCustomProperties();
            cp.addProperty(PROP, MAPPER.writeValueAsString(versMap(m)));

            doc.write(bos);
            return bos.toByteArray();
        }
    }

    /** Reconstruit un ModeleMessage à partir d'un .docx exporté (lecture de la propriété JSON). */
    @SuppressWarnings("unchecked")
    public ModeleMessage importer(byte[] contenu) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(contenu))) {
            POIXMLProperties.CustomProperties cp = doc.getProperties().getCustomProperties();
            if (!cp.contains(PROP)) {
                throw new ValidationException("fichier",
                        "Ce fichier .docx n'est pas un modèle UbiSenderPro exporté (données absentes).");
            }
            String json = cp.getProperty(PROP).getLpwstr();
            Map<String, Object> map = MAPPER.readValue(json, Map.class);
            return depuisMap(map);
        }
    }

    /* ---------- Sérialisation ---------- */

    private Map<String, Object> versMap(ModeleMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nom", m.getNom());
        map.put("typeModele", m.getTypeModele());
        map.put("langue", m.getLangue());
        map.put("categorie", m.getCategorie());
        map.put("enteteTexte", m.getEnteteTexte());
        map.put("enteteMediaType", m.getEnteteMediaType());
        map.put("enteteMediaUrl", m.getEnteteMediaUrl());
        map.put("corps", m.getCorps());
        map.put("piedDePage", m.getPiedDePage());
        map.put("boutonsJson", m.getBoutonsJson());
        map.put("nomModeleWhatsapp", m.getNomModeleWhatsapp());
        map.put("segmentationId", m.getSegmentationId());
        map.put("statutApprobation", m.getStatutApprobation());
        return map;
    }

    private ModeleMessage depuisMap(Map<String, Object> map) {
        ModeleMessage m = new ModeleMessage();
        m.setNom(str(map.get("nom")));
        m.setTypeModele(defaut(str(map.get("typeModele")), "marketing"));
        m.setLangue(defaut(str(map.get("langue")), "fr"));
        m.setCategorie(str(map.get("categorie")));
        m.setEnteteTexte(str(map.get("enteteTexte")));
        m.setEnteteMediaType(str(map.get("enteteMediaType")));
        m.setEnteteMediaUrl(str(map.get("enteteMediaUrl")));
        m.setCorps(defaut(str(map.get("corps")), ""));
        m.setPiedDePage(str(map.get("piedDePage")));
        m.setBoutonsJson(str(map.get("boutonsJson")));
        m.setNomModeleWhatsapp(str(map.get("nomModeleWhatsapp")));
        m.setStatutApprobation(defaut(str(map.get("statutApprobation")), "BROUILLON"));
        Object seg = map.get("segmentationId");
        if (seg != null) {
            try { m.setSegmentationId(Long.valueOf(String.valueOf(((Number) seg).longValue()))); }
            catch (Exception e) { /* segmentation inconnue : ignorée */ }
        }
        return m;
    }

    /* ---------- Helpers POI ---------- */

    private void titre(XWPFDocument doc, String texte) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setSpacingBefore(160);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(14);
        r.setText(texte);
    }

    private void champ(XWPFDocument doc, String libelle, String valeur) {
        if (valeur == null || valeur.isEmpty()) { return; }
        XWPFParagraph p = doc.createParagraph();
        XWPFRun lib = p.createRun();
        lib.setBold(true);
        lib.setText(libelle + " : ");
        XWPFRun val = p.createRun();
        val.setText(valeur);
    }

    private void corps(XWPFDocument doc, String texte) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        String[] lignes = texte.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) { r.addBreak(); }
            r.setText(lignes[i]);
        }
    }

    /* ---------- Divers ---------- */

    private String segmentationLibelle(Long id) {
        if (id == null) { return null; }
        SegmentationClient s = em.find(SegmentationClient.class, id);
        return s == null ? null : s.getLibelle();
    }

    private String assainir(String s) {
        if (s == null || s.isEmpty()) { return "modele"; }
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return n.isEmpty() ? "modele" : n.toLowerCase();
    }

    private String nvl(String s) { return s == null ? "" : s; }
    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private String defaut(String v, String d) { return (v == null || v.isEmpty()) ? d : v; }
}
