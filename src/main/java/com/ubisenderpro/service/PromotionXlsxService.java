package com.ubisenderpro.service;

import com.ubisenderpro.entity.PromotionProduit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Génère le classeur Excel (.xlsx) « liste des produits » d'une promotion,
 * destiné à être joint en pièce jointe (en-tête document) de la campagne.
 */
@Stateless
public class PromotionXlsxService {

    public static final String MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @EJB
    private PromotionProduitService promotionProduitService;

    /** Construit un .xlsx listant les produits actifs de la promotion. */
    public byte[] genererClasseurProduits(Long promotionId) {
        List<PromotionProduit> produits = promotionProduitService.lister(promotionId);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Produits");

            CellStyle entete = wb.createCellStyle();
            Font gras = wb.createFont();
            gras.setBold(true);
            entete.setFont(gras);

            String[] cols = { "CIP7", "CIP13", "Produit", "Qté min. commandée", "Unité gratuite (UG)" };
            int[] largeurs = { 14, 18, 42, 20, 22 };
            Row h = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(entete);
                sheet.setColumnWidth(i, largeurs[i] * 256);
            }

            int r = 1;
            for (PromotionProduit p : produits) {
                if (!p.isActif()) { continue; }
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nz(p.getCip7()));
                row.createCell(1).setCellValue(nz(p.getCip13()));
                row.createCell(2).setCellValue(nz(p.getNomProduit()));
                if (p.getQuantiteMinimale() != null) { row.createCell(3).setCellValue(p.getQuantiteMinimale()); }
                row.createCell(4).setCellValue(ug(p));
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Génération du classeur de promotion impossible : " + e.getMessage(), e);
        }
    }

    /** Représentation lisible de l'UG : « 20 % » ou « 20 u. ». */
    private String ug(PromotionProduit p) {
        if (p.getTauxUg() != null && p.getTauxUg().signum() > 0) {
            return p.getTauxUg().stripTrailingZeros().toPlainString() + " %";
        }
        if (p.getQuantiteUg() != null && p.getQuantiteUg() > 0) {
            return p.getQuantiteUg() + " u.";
        }
        return "";
    }

    private String nz(String s) { return s == null ? "" : s; }
}
