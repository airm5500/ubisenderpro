package com.ubisenderpro.service;

import com.ubisenderpro.entity.Article;
import com.ubisenderpro.entity.Promotion;
import com.ubisenderpro.entity.PromotionProduit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produits d'une promotion : CRUD, contrôles métier (§7) et import Excel (§6).
 */
@Stateless
public class PromotionProduitService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private PromotionService promotionService;

    public List<PromotionProduit> lister(Long promotionId) {
        return em.createQuery(
                "SELECT pp FROM PromotionProduit pp WHERE pp.promotionId = :id ORDER BY pp.nomProduit", PromotionProduit.class)
                .setParameter("id", promotionId).getResultList();
    }

    public PromotionProduit creer(Long promotionId, PromotionProduit p) {
        p.setPromotionId(promotionId);
        valider(p, null);
        resoudreArticle(p);
        if (doublon(promotionId, p.getCip7(), p.getCip13(), null)) {
            throw new ValidationException("cip",
                    "Ce produit (CIP " + cipAffiche(p) + ") est déjà présent dans cette promotion.");
        }
        em.persist(p);
        return p;
    }

    public PromotionProduit modifier(Long id, PromotionProduit data) {
        PromotionProduit p = em.find(PromotionProduit.class, id);
        if (p == null) { return null; }
        p.setCip7(data.getCip7());
        p.setCip13(data.getCip13());
        p.setNomProduit(data.getNomProduit());
        p.setQuantiteMinimale(data.getQuantiteMinimale());
        p.setTauxUg(data.getTauxUg());
        p.setTauxMaxUg(data.getTauxMaxUg());
        p.setQuantiteUg(data.getQuantiteUg());
        p.setQuantiteUgMax(data.getQuantiteUgMax());
        p.setModeCalcul(data.getModeCalcul());
        p.setActif(data.isActif());
        valider(p, id);
        resoudreArticle(p);
        if (doublon(p.getPromotionId(), p.getCip7(), p.getCip13(), id)) {
            throw new ValidationException("cip", "Un autre produit identique existe déjà dans cette promotion.");
        }
        return em.merge(p);
    }

    public void supprimer(Long id) {
        PromotionProduit p = em.find(PromotionProduit.class, id);
        if (p != null) { em.remove(p); }
    }

    /** Contrôles métier §7 (hors unicité, gérée à part). */
    private void valider(PromotionProduit p, Long id) {
        boolean cip = (p.getCip7() != null && !p.getCip7().trim().isEmpty())
                || (p.getCip13() != null && !p.getCip13().trim().isEmpty());
        if (!cip) { throw new ValidationException("cip", "Le CIP7 ou le CIP13 est obligatoire."); }
        boolean aTaux = p.getTauxUg() != null && p.getTauxUg().signum() > 0;
        boolean aQte = p.getQuantiteUg() != null && p.getQuantiteUg() > 0;
        if (!aTaux && !aQte) {
            throw new ValidationException("tauxUg", "Renseignez au moins un taux UG ou une quantité UG.");
        }
        if (aTaux && p.getTauxMaxUg() != null && p.getTauxUg().compareTo(p.getTauxMaxUg()) > 0) {
            throw new ValidationException("tauxUg", "Le taux UG dépasse le taux maximal autorisé.");
        }
        if (negatif(p.getTauxUg()) || negatif(p.getTauxMaxUg())
                || (p.getQuantiteUg() != null && p.getQuantiteUg() < 0)
                || (p.getQuantiteUgMax() != null && p.getQuantiteUgMax() < 0)
                || (p.getQuantiteMinimale() != null && p.getQuantiteMinimale() < 0)) {
            throw new ValidationException("quantite", "Les quantités et taux ne peuvent pas être négatifs.");
        }
        if (p.getModeCalcul() == null || p.getModeCalcul().isEmpty()) {
            p.setModeCalcul(aTaux && aQte ? "MIXTE" : (aTaux ? "TAUX" : "QUANTITE"));
        }
    }

    private boolean negatif(BigDecimal v) { return v != null && v.signum() < 0; }

    /** Rattache l'article du catalogue par CIP7=cip ou CIP13=code-barres (si trouvé). */
    private void resoudreArticle(PromotionProduit p) {
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

    private boolean doublon(Long promotionId, String cip7, String cip13, Long excludeId) {
        for (PromotionProduit p : lister(promotionId)) {
            if (excludeId != null && excludeId.equals(p.getId())) { continue; }
            if (cip7 != null && !cip7.isEmpty() && cip7.equals(p.getCip7())) { return true; }
            if (cip13 != null && !cip13.isEmpty() && cip13.equals(p.getCip13())) { return true; }
        }
        return false;
    }

    private String cipAffiche(PromotionProduit p) {
        return (p.getCip7() != null && !p.getCip7().isEmpty()) ? p.getCip7() : p.getCip13();
    }

    /**
     * Importe les produits d'un fichier Excel dans la promotion (colonnes :
     * CIP7, CIP13, Produit, Date début, Date fin, Taux max UG possible).
     * @return rapport { total, crees, majs, ignores, erreurs:[{ligne,raison}] }.
     */
    public Map<String, Object> importer(Long promotionId, byte[] contenu) throws Exception {
        Promotion promo = em.find(Promotion.class, promotionId);
        if (promo == null) { throw new ValidationException("promotion", "Promotion introuvable."); }
        int crees = 0, majs = 0, ignores = 0;
        List<Map<String, Object>> erreurs = new ArrayList<>();
        LocalDateTime minDebut = null, maxFin = null;

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
                LocalDateTime d = date(row, col[3]);
                LocalDateTime f = date(row, col[4]);
                BigDecimal tauxMax = nombre(row, col[5], fmt);
                if (d != null && (minDebut == null || d.isBefore(minDebut))) { minDebut = d; }
                if (f != null && (maxFin == null || f.isAfter(maxFin))) { maxFin = f; }

                PromotionProduit ex = existant(promotionId, cip7, cip13);
                PromotionProduit p = ex != null ? ex : new PromotionProduit();
                p.setPromotionId(promotionId);
                p.setCip7(cip7); p.setCip13(cip13);
                if (!nom.isEmpty()) { p.setNomProduit(nom); }
                if (tauxMax != null) { p.setTauxMaxUg(tauxMax); }
                // Import : le détail UG (taux/quantité) se complète ensuite dans la fiche.
                if (p.getModeCalcul() == null) { p.setModeCalcul("TAUX"); }
                resoudreArticle(p);
                try {
                    if (ex != null) { em.merge(p); majs++; }
                    else { em.persist(p); crees++; }
                } catch (Exception e) {
                    erreurs.add(erreur(r + 1, "Enregistrement impossible : " + e.getMessage()));
                }
            }

            // Cadre la période de la promotion d'après le fichier (si non définie).
            boolean maj = false;
            if (promo.getDateDebut() == null && minDebut != null) { promo.setDateDebut(minDebut); maj = true; }
            if (promo.getDateFin() == null && maxFin != null) { promo.setDateFin(maxFin); maj = true; }
            if (maj && !"ANNULEE".equals(promo.getStatut()) && !"ARCHIVEE".equals(promo.getStatut())) {
                promo.setStatut(promotionService.statutAuto(promo));
                promo.setActif("ACTIVE".equals(promo.getStatut()));
                em.merge(promo);
            }

            Map<String, Object> rapport = new LinkedHashMap<>();
            rapport.put("total", total);
            rapport.put("crees", crees);
            rapport.put("majs", majs);
            rapport.put("ignores", ignores);
            rapport.put("erreurs", erreurs);
            return rapport;
        }
    }

    private PromotionProduit existant(Long promotionId, String cip7, String cip13) {
        for (PromotionProduit p : lister(promotionId)) {
            if (!cip7.isEmpty() && cip7.equals(p.getCip7())) { return p; }
            if (!cip13.isEmpty() && cip13.equals(p.getCip13())) { return p; }
        }
        return null;
    }

    /** Repère les colonnes par en-tête (ordre par défaut si en-têtes absents). */
    private int[] entetes(Row header, DataFormatter fmt) {
        int[] c = { 0, 1, 2, 3, 4, 5 };
        if (header == null) { return c; }
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String h = java.text.Normalizer.normalize(txt(header, i, fmt).toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            if (h.contains("cip7")) { c[0] = i; }
            else if (h.contains("cip13")) { c[1] = i; }
            else if (h.contains("produit") || h.contains("nom")) { c[2] = i; }
            else if (h.contains("debut")) { c[3] = i; }
            else if (h.contains("fin")) { c[4] = i; }
            else if (h.contains("taux")) { c[5] = i; }
        }
        return c;
    }

    private String txt(Row row, int idx, DataFormatter fmt) {
        if (row == null || idx < 0) { return ""; }
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private LocalDateTime date(Row row, int idx) {
        if (row == null || idx < 0) { return null; }
        Cell c = row.getCell(idx);
        if (c == null) { return null; }
        try {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return DateUtil.getJavaDate(c.getNumericCellValue()).toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }
            String s = c.getStringCellValue().trim();
            if (s.isEmpty()) { return null; }
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception e) { return null; }
    }

    private BigDecimal nombre(Row row, int idx, DataFormatter fmt) {
        String s = txt(row, idx, fmt).replace(",", ".").replaceAll("[^0-9.]", "");
        if (s.isEmpty()) { return null; }
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private Map<String, Object> erreur(int ligne, String raison) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ligne", ligne);
        m.put("raison", raison);
        return m;
    }
}
