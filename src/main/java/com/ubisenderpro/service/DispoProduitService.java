package com.ubisenderpro.service;

import com.ubisenderpro.entity.Article;
import com.ubisenderpro.entity.DispoEvenement;
import com.ubisenderpro.entity.DispoProduit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produits d'un événement de disponibilité / rupture : CRUD, rattachement
 * catalogue, contrôle anti-doublon et import Excel.
 */
@Stateless
public class DispoProduitService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    public List<DispoProduit> lister(Long evenementId) {
        return em.createQuery(
                "SELECT p FROM DispoProduit p WHERE p.evenementId = :id ORDER BY p.nomProduit", DispoProduit.class)
                .setParameter("id", evenementId).getResultList();
    }

    public DispoProduit creer(Long evenementId, DispoProduit p) {
        p.setEvenementId(evenementId);
        valider(p);
        resoudreArticle(p);
        appliquerStatutSuggere(p, evenementId);
        if (doublon(evenementId, p.getCip7(), p.getCip13(), null)) {
            throw new ValidationException("cip",
                    "Ce produit (CIP " + cipAffiche(p) + ") est déjà présent dans cet événement.");
        }
        em.persist(p);
        return p;
    }

    public DispoProduit modifier(Long id, DispoProduit data) {
        DispoProduit p = em.find(DispoProduit.class, id);
        if (p == null) { return null; }
        p.setCip7(data.getCip7());
        p.setCip13(data.getCip13());
        p.setNomProduit(data.getNomProduit());
        p.setQuantiteDisponible(data.getQuantiteDisponible());
        p.setSeuilRupture(data.getSeuilRupture());
        p.setCouvertureJours(data.getCouvertureJours());
        p.setDatePeremption(data.getDatePeremption());
        p.setNumeroLot(data.getNumeroLot());
        p.setAgence(data.getAgence());
        p.setStockLimite(data.isStockLimite());
        p.setLienReservation(data.getLienReservation());
        if (data.getStatut() != null && !data.getStatut().isEmpty()) { p.setStatut(data.getStatut()); }
        p.setActif(data.isActif());
        valider(p);
        resoudreArticle(p);
        if (p.getStatut() == null || p.getStatut().isEmpty()) { appliquerStatutSuggere(p, p.getEvenementId()); }
        if (doublon(p.getEvenementId(), p.getCip7(), p.getCip13(), id)) {
            throw new ValidationException("cip", "Un autre produit identique existe déjà dans cet événement.");
        }
        return em.merge(p);
    }

    public void supprimer(Long id) {
        DispoProduit p = em.find(DispoProduit.class, id);
        if (p != null) { em.remove(p); }
    }

    private void valider(DispoProduit p) {
        boolean cip = (p.getCip7() != null && !p.getCip7().trim().isEmpty())
                || (p.getCip13() != null && !p.getCip13().trim().isEmpty());
        if (!cip) { throw new ValidationException("cip", "Le CIP7 ou le CIP13 est obligatoire."); }
        if (p.getQuantiteDisponible() != null && p.getQuantiteDisponible() < 0) {
            throw new ValidationException("quantiteDisponible", "La quantité disponible ne peut pas être négative.");
        }
        if (p.getSeuilRupture() != null && p.getSeuilRupture() < 0) {
            throw new ValidationException("seuilRupture", "Le seuil de rupture ne peut pas être négatif.");
        }
    }

    /** Statut produit suggéré d'après le type d'événement, puis les quantités. */
    private void appliquerStatutSuggere(DispoProduit p, Long evenementId) {
        if (p.getStatut() != null && !p.getStatut().isEmpty()) { return; }
        DispoEvenement e = em.find(DispoEvenement.class, evenementId);
        String type = e != null ? e.getType() : null;
        if ("RETOUR_RUPTURE".equals(type)) { p.setStatut("RETOUR_RUPTURE"); return; }
        if ("RUPTURE_CONFIRMEE".equals(type)) { p.setStatut("EN_RUPTURE"); return; }
        if ("RISQUE_RUPTURE".equals(type)) { p.setStatut("RISQUE_RUPTURE"); return; }
        Integer q = p.getQuantiteDisponible(), s = p.getSeuilRupture();
        if (q != null && q <= 0) { p.setStatut("EN_RUPTURE"); }
        else if (p.isStockLimite() || (q != null && s != null && q <= s)) { p.setStatut("STOCK_LIMITE"); }
        else { p.setStatut("DISPONIBLE"); }
    }

    /** Rattache l'article du catalogue par CIP7=cip ou CIP13=code-barres (si trouvé). */
    private void resoudreArticle(DispoProduit p) {
        Article a = null;
        if (p.getCip7() != null && !p.getCip7().trim().isEmpty()) {
            a = unArticle("SELECT a FROM Article a WHERE a.cip = :v", p.getCip7().trim());
        }
        if (a == null && p.getCip13() != null && !p.getCip13().trim().isEmpty()) {
            a = unArticle("SELECT a FROM Article a WHERE a.codeBarres = :v", p.getCip13().trim());
        }
        if (a != null) {
            p.setArticleId(a.getId());
            if (p.getNomProduit() == null || p.getNomProduit().isEmpty()) { p.setNomProduit(a.getDesignation()); }
        }
    }

    private Article unArticle(String jpql, String v) {
        List<Article> l = em.createQuery(jpql, Article.class).setParameter("v", v).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private boolean doublon(Long evenementId, String cip7, String cip13, Long excludeId) {
        for (DispoProduit p : lister(evenementId)) {
            if (excludeId != null && excludeId.equals(p.getId())) { continue; }
            if (cip7 != null && !cip7.isEmpty() && cip7.equals(p.getCip7())) { return true; }
            if (cip13 != null && !cip13.isEmpty() && cip13.equals(p.getCip13())) { return true; }
        }
        return false;
    }

    private String cipAffiche(DispoProduit p) {
        return (p.getCip7() != null && !p.getCip7().isEmpty()) ? p.getCip7() : p.getCip13();
    }

    /**
     * Importe les produits d'un fichier Excel dans l'événement.
     * Colonnes reconnues (par en-tête) : CIP7, CIP13, Nom produit, Quantité,
     * Seuil, Couverture, Péremption, Lot, Agence, Lien réservation.
     * @return rapport { total, crees, majs, erreurs:[{ligne,raison}] }.
     */
    public Map<String, Object> importer(Long evenementId, byte[] contenu) throws Exception {
        DispoEvenement evt = em.find(DispoEvenement.class, evenementId);
        if (evt == null) { throw new ValidationException("evenement", "Événement introuvable."); }
        int crees = 0, majs = 0;
        List<Map<String, Object>> erreurs = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int[] col = entetes(sheet.getRow(sheet.getFirstRowNum()), fmt);
            int total = 0;
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) { continue; }
                String cip7 = txt(row, col[0], fmt);
                String cip13 = txt(row, col[1], fmt);
                String nom = txt(row, col[2], fmt);
                if (cip7.isEmpty() && cip13.isEmpty() && nom.isEmpty()) { continue; }
                total++;
                if (cip7.equals("0")) { cip7 = ""; }
                if (cip13.equals("0")) { cip13 = ""; }
                if (cip7.isEmpty() && cip13.isEmpty()) {
                    erreurs.add(erreur(r + 1, "CIP7 ou CIP13 obligatoire")); continue;
                }

                DispoProduit ex = existant(evenementId, cip7, cip13);
                DispoProduit p = ex != null ? ex : new DispoProduit();
                p.setEvenementId(evenementId);
                p.setCip7(cip7); p.setCip13(cip13);
                if (!nom.isEmpty()) { p.setNomProduit(nom); }
                p.setQuantiteDisponible(entier(row, col[3], fmt));
                p.setSeuilRupture(entier(row, col[4], fmt));
                p.setCouvertureJours(entier(row, col[5], fmt));
                LocalDate per = date(row, col[6]);
                if (per != null) { p.setDatePeremption(per); }
                String lot = txt(row, col[7], fmt);
                if (!lot.isEmpty()) { p.setNumeroLot(lot); }
                String ag = txt(row, col[8], fmt);
                if (!ag.isEmpty()) { p.setAgence(ag); }
                String lien = txt(row, col[9], fmt);
                if (!lien.isEmpty()) { p.setLienReservation(lien); }
                resoudreArticle(p);
                appliquerStatutSuggere(p, evenementId);
                try {
                    if (ex != null) { em.merge(p); majs++; }
                    else { em.persist(p); crees++; }
                } catch (Exception e) {
                    erreurs.add(erreur(r + 1, "Enregistrement impossible : " + e.getMessage()));
                }
            }

            Map<String, Object> rapport = new LinkedHashMap<>();
            rapport.put("total", total);
            rapport.put("crees", crees);
            rapport.put("majs", majs);
            rapport.put("erreurs", erreurs);
            return rapport;
        }
    }

    private DispoProduit existant(Long evenementId, String cip7, String cip13) {
        for (DispoProduit p : lister(evenementId)) {
            if (!cip7.isEmpty() && cip7.equals(p.getCip7())) { return p; }
            if (!cip13.isEmpty() && cip13.equals(p.getCip13())) { return p; }
        }
        return null;
    }

    /** Repère les colonnes par en-tête (ordre par défaut si en-têtes absents). */
    private int[] entetes(Row header, DataFormatter fmt) {
        int[] c = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        if (header == null) { return c; }
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String h = java.text.Normalizer.normalize(txt(header, i, fmt).toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            if (h.contains("cip7")) { c[0] = i; }
            else if (h.contains("cip13")) { c[1] = i; }
            else if (h.contains("produit") || h.contains("nom")) { c[2] = i; }
            else if (h.contains("quantite") || h.contains("dispo")) { c[3] = i; }
            else if (h.contains("seuil")) { c[4] = i; }
            else if (h.contains("couverture")) { c[5] = i; }
            else if (h.contains("peremption") || h.contains("peremp")) { c[6] = i; }
            else if (h.contains("lot")) { c[7] = i; }
            else if (h.contains("agence")) { c[8] = i; }
            else if (h.contains("reservation") || h.contains("lien")) { c[9] = i; }
        }
        return c;
    }

    private String txt(Row row, int idx, DataFormatter fmt) {
        if (row == null || idx < 0) { return ""; }
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private Integer entier(Row row, int idx, DataFormatter fmt) {
        String s = txt(row, idx, fmt).replaceAll("[^0-9-]", "");
        if (s.isEmpty()) { return null; }
        try { return Integer.valueOf(s); } catch (Exception e) { return null; }
    }

    private LocalDate date(Row row, int idx) {
        if (row == null || idx < 0) { return null; }
        Cell c = row.getCell(idx);
        if (c == null) { return null; }
        try {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return DateUtil.getJavaDate(c.getNumericCellValue()).toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
            String s = c.getStringCellValue().trim();
            if (s.isEmpty()) { return null; }
            return LocalDate.parse(s);
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> erreur(int ligne, String raison) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ligne", ligne);
        m.put("raison", raison);
        return m;
    }
}
