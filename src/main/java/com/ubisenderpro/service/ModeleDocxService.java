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

    private static final String CORPS_DEBUT = "----- CORPS DU MESSAGE (début) -----";
    private static final String CORPS_FIN = "----- CORPS DU MESSAGE (fin) -----";

    // Étiquettes lisibles <-> champs (pour reprendre les retouches manuelles à l'import).
    private static final String L_NOM = "Nom";
    private static final String L_TYPE = "Type";
    private static final String L_LANGUE = "Langue";
    private static final String L_CATEGORIE = "Catégorie";
    private static final String L_ENT_TXT = "En-tête (texte)";
    private static final String L_ENT_MTYPE = "En-tête média (type)";
    private static final String L_ENT_MURL = "En-tête média (URL)";
    private static final String L_PIED = "Pied de page";
    private static final String L_BOUTONS = "Boutons (JSON)";
    private static final String L_META = "Nom du modèle Meta";
    private static final String L_APPRO = "Approbation";

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
            champ(doc, L_NOM, m.getNom());
            champ(doc, L_TYPE, m.getTypeModele());
            champ(doc, L_LANGUE, m.getLangue());
            champ(doc, L_CATEGORIE, m.getCategorie());
            champ(doc, "Segmentation", seg == null ? "Toutes les segmentations" : seg);
            champ(doc, L_ENT_TXT, m.getEnteteTexte());
            champ(doc, L_ENT_MTYPE, m.getEnteteMediaType());
            champ(doc, L_ENT_MURL, m.getEnteteMediaUrl());
            champ(doc, L_PIED, m.getPiedDePage());
            champ(doc, L_BOUTONS, m.getBoutonsJson());
            champ(doc, L_META, m.getNomModeleWhatsapp());
            champ(doc, L_APPRO, m.getStatutApprobation());

            // Corps encadré par des marqueurs ; une ligne = un paragraphe (édition libre dans Word).
            ligne(doc, CORPS_DEBUT, true);
            for (String l : nvl(m.getCorps()).split("\n", -1)) { ligne(doc, l, false); }
            ligne(doc, CORPS_FIN, true);

            // Données structurées pour le ré-import.
            POIXMLProperties.CustomProperties cp = doc.getProperties().getCustomProperties();
            cp.addProperty(PROP, MAPPER.writeValueAsString(versMap(m)));

            doc.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * Reconstruit un ModeleMessage depuis un .docx exporté.
     * Les données structurées (JSON) servent de base ; les retouches manuelles du
     * contenu lisible (étiquettes + corps) les remplacent quand elles diffèrent.
     */
    @SuppressWarnings("unchecked")
    public ModeleMessage importer(byte[] contenu) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(contenu))) {
            POIXMLProperties.CustomProperties cp = doc.getProperties().getCustomProperties();
            ModeleMessage m;
            if (cp.contains(PROP)) {
                m = depuisMap(MAPPER.readValue(cp.getProperty(PROP).getLpwstr(), Map.class));
            } else {
                m = new ModeleMessage();
                m.setTypeModele("marketing");
                m.setLangue("fr");
                m.setStatutApprobation("BROUILLON");
            }
            appliquerRetouches(doc, m);
            if ((m.getNom() == null || m.getNom().isEmpty()) && (m.getCorps() == null || m.getCorps().isEmpty())) {
                throw new ValidationException("fichier",
                        "Ce fichier .docx n'est pas un modèle UbiSenderPro reconnaissable.");
            }
            if (m.getCorps() == null) { m.setCorps(""); }
            return m;
        }
    }

    /** Lit le contenu lisible (étiquettes + corps) et applique les valeurs au modèle. */
    private void appliquerRetouches(XWPFDocument doc, ModeleMessage m) {
        Map<String, String> champs = new LinkedHashMap<>();
        StringBuilder corps = new StringBuilder();
        boolean dansCorps = false, corpsTrouve = false;
        for (XWPFParagraph p : doc.getParagraphs()) {
            String txt = p.getText();
            if (txt == null) { continue; }
            String t = txt.trim();
            if (t.equals(CORPS_DEBUT)) { dansCorps = true; corpsTrouve = true; continue; }
            if (t.equals(CORPS_FIN)) { dansCorps = false; continue; }
            if (dansCorps) { if (corps.length() > 0) { corps.append("\n"); } corps.append(txt); continue; }
            int idx = txt.indexOf(" : ");
            if (idx > 0) { champs.put(txt.substring(0, idx).trim(), txt.substring(idx + 3)); }
        }
        appliquer(champs, L_NOM, m::setNom);
        appliquer(champs, L_TYPE, m::setTypeModele);
        appliquer(champs, L_LANGUE, m::setLangue);
        appliquer(champs, L_CATEGORIE, m::setCategorie);
        appliquer(champs, L_ENT_TXT, m::setEnteteTexte);
        appliquer(champs, L_ENT_MTYPE, m::setEnteteMediaType);
        appliquer(champs, L_ENT_MURL, m::setEnteteMediaUrl);
        appliquer(champs, L_PIED, m::setPiedDePage);
        appliquer(champs, L_BOUTONS, m::setBoutonsJson);
        appliquer(champs, L_META, m::setNomModeleWhatsapp);
        appliquer(champs, L_APPRO, m::setStatutApprobation);
        if (corpsTrouve) { m.setCorps(corps.toString()); }
    }

    private void appliquer(Map<String, String> champs, String label, java.util.function.Consumer<String> setter) {
        if (champs.containsKey(label)) { setter.accept(champs.get(label)); }
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

    /** Écrit une ligne (un paragraphe). marqueur = ligne grisée/italique de repère. */
    private void ligne(XWPFDocument doc, String texte, boolean marqueur) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        if (marqueur) { r.setItalic(true); r.setColor("888888"); }
        r.setText(texte == null ? "" : texte);
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
