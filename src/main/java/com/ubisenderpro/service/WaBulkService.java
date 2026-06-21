package com.ubisenderpro.service;

import com.ubisenderpro.dto.WaBulkRequest;
import com.ubisenderpro.entity.WaBulkDestinataire;
import com.ubisenderpro.entity.WaBulkJob;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Orchestration de l'envoi en masse WhatsApp Web : création du travail,
 * parsing des destinataires, lancement asynchrone, suivi.
 */
@Stateless
public class WaBulkService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private WaBulkSenderAsync sender;

    @EJB
    private ParametreService parametreService;

    public WaBulkJob creer(WaBulkRequest req) {
        if (req.getSessionId() == null) throw new IllegalArgumentException("Session requise");

        WaBulkJob j = new WaBulkJob();
        j.setSessionId(req.getSessionId());
        j.setNom(req.getNom());
        j.setMsg1(req.getMsg1()); j.setMsg2(req.getMsg2()); j.setMsg3(req.getMsg3());
        j.setMsg4(req.getMsg4()); j.setMsg5(req.getMsg5());
        j.setMediaUrl(vide(req.getMediaUrl()) ? null : req.getMediaUrl());
        j.setMediaType(req.getMediaType());
        j.setMediaMime(req.getMediaMime());
        j.setMediaNom(req.getMediaNom());
        if (req.getAttenteMin() != null) j.setAttenteMin(req.getAttenteMin());
        if (req.getAttenteMax() != null) j.setAttenteMax(req.getAttenteMax());
        if (req.getPauseApres() != null) j.setPauseApres(req.getPauseApres());
        if (req.getPauseMin() != null) j.setPauseMin(req.getPauseMin());
        if (req.getPauseMax() != null) j.setPauseMax(req.getPauseMax());
        if (req.getHeureDebut() != null) j.setHeureDebut(req.getHeureDebut());
        if (req.getHeureFin() != null) j.setHeureFin(req.getHeureFin());
        if (Boolean.TRUE.equals(req.getPlanifier())) {
            j.setDateProgrammee(parseDate(req.getDateProgrammee()));
            j.setStatut("PLANIFIEE");
        }

        if (vide(j.getMsg1()) && vide(j.getMsg2()) && vide(j.getMsg3())
                && vide(j.getMsg4()) && vide(j.getMsg5()) && vide(j.getMediaUrl())) {
            throw new IllegalArgumentException("Renseignez au moins une variante de message ou une pièce jointe");
        }

        em.persist(j);
        em.flush();

        int total = 0;
        for (String ligne : decouper(req.getDestinatairesTexte())) {
            String[] parts = ligne.split("[;,\\t]", 2);
            String numero = parts[0].replaceAll("[^0-9]", "");
            if (numero.length() < 6) { continue; }
            String nom = parts.length > 1 ? parts[1].trim() : null;
            WaBulkDestinataire d = new WaBulkDestinataire();
            d.setJobId(j.getId());
            d.setNumero(numero);
            d.setNom(nom);
            em.persist(d);
            total++;
        }
        j.setTotal(total);
        return em.merge(j);
    }

    public WaBulkJob lancer(Long jobId) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        if (j == null) throw new IllegalArgumentException("Travail introuvable");
        if (j.getTotal() == 0) throw new IllegalArgumentException("Aucun destinataire");
        sender.lancer(jobId);
        return j;
    }

    public List<WaBulkJob> lister() {
        List<WaBulkJob> jobs = em.createQuery("SELECT j FROM WaBulkJob j ORDER BY j.id DESC", WaBulkJob.class)
                .setMaxResults(100).getResultList();
        for (WaBulkJob j : jobs) {
            if (j.getEchoues() > 0) {
                List<String> err = em.createQuery(
                        "SELECT d.erreur FROM WaBulkDestinataire d WHERE d.jobId = :j " +
                        "AND d.statut = 'ECHEC' AND d.erreur IS NOT NULL ORDER BY d.id DESC", String.class)
                        .setParameter("j", j.getId()).setMaxResults(1).getResultList();
                if (!err.isEmpty()) { j.setDerniereErreur(err.get(0)); }
            }
        }
        return jobs;
    }

    public Optional<WaBulkJob> parId(Long id) { return Optional.ofNullable(em.find(WaBulkJob.class, id)); }

    /**
     * Analyse un fichier CSV/Excel de destinataires (structure « numero;nomclient »)
     * SANS rien enregistrer : retourne les lignes conformes et non conformes
     * (avec motif), pour un envoi ponctuel ou une réconciliation côté interface.
     */
    public Map<String, Object> preparerFichier(String fichierBase64, String nomFichier) {
        if (fichierBase64 == null || fichierBase64.isEmpty()) {
            throw new IllegalArgumentException("Fichier manquant");
        }
        byte[] data;
        try { data = Base64.getDecoder().decode(fichierBase64.replaceAll("\\s", "")); }
        catch (Exception e) { throw new IllegalArgumentException("Fichier illisible (base64)"); }

        List<String[]> brutes = lireLignesFichier(data, nomFichier);
        String prefixe = parametreService.valeur("whatsapp.prefixe_pays", "");

        List<Map<String, String>> valides = new ArrayList<>();
        List<Map<String, String>> invalides = new ArrayList<>();
        for (String[] r : brutes) {
            String numeroBrut = r[0] == null ? "" : r[0].trim();
            String nom = r.length > 1 && r[1] != null ? r[1].trim() : "";
            String raison = motifNonConforme(numeroBrut, prefixe);
            if (raison == null) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("numero", normaliserNumero(numeroBrut, prefixe));
                m.put("nom", nom);
                valides.add(m);
            } else {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("numero", numeroBrut);
                m.put("nom", nom);
                m.put("raison", raison);
                invalides.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valides", valides);
        out.put("invalides", invalides);
        out.put("total", brutes.size());
        return out;
    }

    /** @return motif de non-conformité, ou null si le numéro est conforme. */
    private String motifNonConforme(String numeroBrut, String prefixe) {
        String chiffres = numeroBrut == null ? "" : numeroBrut.replaceAll("[^0-9]", "");
        if (chiffres.isEmpty()) { return "Numéro manquant"; }
        String norm = normaliserNumero(numeroBrut, prefixe);
        if (norm.length() < 8) { return "Numéro trop court"; }
        if (norm.length() > 15) { return "Numéro trop long"; }
        return null;
    }

    /** Normalise un numéro : chiffres seuls, préfixe pays ajouté si saisie locale. */
    private String normaliserNumero(String numero, String prefixe) {
        String d = numero == null ? "" : numero.replaceAll("[^0-9]", "");
        if (d.isEmpty()) { return ""; }
        if (prefixe != null && !prefixe.isEmpty() && !d.startsWith(prefixe) && d.length() <= 10) {
            d = prefixe + d.replaceAll("^0+", "");
        }
        return d;
    }

    /** Lit un fichier CSV (numero;nom par ligne) ou Excel (.xlsx) en lignes positionnelles [numero, nom]. */
    private List<String[]> lireLignesFichier(byte[] data, String nomFichier) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase();
        if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) { return lireExcel(data); }
        return lireCsv(data);
    }

    private List<String[]> lireCsv(byte[] data) {
        List<String[]> lignes = new ArrayList<>();
        String contenu = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        for (String ligne : contenu.split("\\r?\\n")) {
            if (ligne.trim().isEmpty()) { continue; }
            String[] parts = ligne.split("[;,\\t]", 2);
            lignes.add(new String[]{ parts[0], parts.length > 1 ? parts[1] : "" });
        }
        // Ignore un éventuel en-tête (ex. « numero;nom »).
        if (!lignes.isEmpty()) {
            String premier = lignes.get(0)[0].replaceAll("[^0-9]", "");
            if (premier.isEmpty()) { lignes.remove(0); }
        }
        return lignes;
    }

    private List<String[]> lireExcel(byte[] data) {
        List<String[]> lignes = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { continue; }
                String numero = celluleTexte(row.getCell(0), fmt);
                String nom = celluleTexte(row.getCell(1), fmt);
                if (numero.isEmpty() && nom.isEmpty()) { continue; }
                lignes.add(new String[]{ numero, nom });
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Fichier Excel illisible : " + e.getMessage());
        }
        if (!lignes.isEmpty()) {
            String premier = lignes.get(0)[0].replaceAll("[^0-9]", "");
            if (premier.isEmpty()) { lignes.remove(0); }
        }
        return lignes;
    }

    private String celluleTexte(Cell cell, DataFormatter fmt) {
        if (cell == null) { return ""; }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) { return String.format("%.0f", d); }
        }
        return fmt.formatCellValue(cell).trim();
    }

    /** Remet les destinataires en échec en attente et relance l'envoi (réussis non touchés). */
    public WaBulkJob relancerEchecs(Long jobId) {
        WaBulkJob j = em.find(WaBulkJob.class, jobId);
        if (j == null) throw new IllegalArgumentException("Travail introuvable");
        int n = em.createQuery(
                "UPDATE WaBulkDestinataire d SET d.statut = 'EN_ATTENTE', d.erreur = NULL " +
                "WHERE d.jobId = :j AND d.statut = 'ECHEC'")
                .setParameter("j", jobId).executeUpdate();
        if (n == 0) throw new IllegalArgumentException("Aucun échec à renvoyer");
        j.setEchoues(0);
        j.setStatut("EN_COURS");
        em.merge(j);
        sender.lancer(jobId);
        return j;
    }

    public List<WaBulkDestinataire> destinataires(Long jobId) {
        return em.createQuery(
                "SELECT d FROM WaBulkDestinataire d WHERE d.jobId = :j ORDER BY d.id", WaBulkDestinataire.class)
                .setParameter("j", jobId).getResultList();
    }

    /** Travaux planifiés dont l'heure de démarrage est atteinte (pour le planificateur). */
    public List<WaBulkJob> planifiesDus() {
        return em.createQuery(
                "SELECT j FROM WaBulkJob j WHERE j.statut = 'PLANIFIEE' " +
                "AND (j.dateProgrammee IS NULL OR j.dateProgrammee <= :now)", WaBulkJob.class)
                .setParameter("now", java.time.LocalDateTime.now()).getResultList();
    }

    /** La plage horaire autorisée du travail est-elle ouverte maintenant ? */
    public static boolean dansFenetre(WaBulkJob j) {
        int d = j.getHeureDebut(), f = j.getHeureFin();
        if (d == f) { return true; } // pas de restriction
        int h = java.time.LocalTime.now().getHour();
        return d < f ? (h >= d && h < f) : (h >= d || h < f); // fenêtre traversant minuit
    }

    private java.time.LocalDateTime parseDate(String s) {
        if (s == null || s.trim().isEmpty()) { return java.time.LocalDateTime.now(); }
        String v = s.trim().replace('T', ' ');
        try {
            if (v.length() <= 16) { // yyyy-MM-dd HH:mm
                return java.time.LocalDateTime.parse(v,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return java.time.LocalDateTime.parse(v.substring(0, 19),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return java.time.LocalDateTime.now();
        }
    }

    private boolean vide(String s) { return s == null || s.trim().isEmpty(); }

    private String[] decouper(String texte) {
        if (texte == null || texte.trim().isEmpty()) { return new String[0]; }
        return texte.split("\\r?\\n");
    }
}
