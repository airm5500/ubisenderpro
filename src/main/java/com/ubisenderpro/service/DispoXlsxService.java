package com.ubisenderpro.service;

import com.ubisenderpro.entity.DispoProduit;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Génère le bulletin Excel (.xlsx) des produits d'un événement de
 * disponibilité / rupture, joint en pièce jointe de l'annonce (spec §15).
 */
@Stateless
public class DispoXlsxService {

    public static final String MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @EJB
    private DispoProduitService produitService;

    public byte[] genererBulletin(Long evenementId) {
        List<DispoProduit> produits = produitService.lister(evenementId);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Bulletin");

            CellStyle entete = wb.createCellStyle();
            Font gras = wb.createFont();
            gras.setBold(true);
            entete.setFont(gras);

            String[] cols = { "CIP7", "CIP13", "Produit", "Statut", "Agence",
                    "Quantité disponible", "Seuil", "Date de péremption", "Lien de réservation" };
            int[] largeurs = { 14, 18, 42, 18, 18, 18, 12, 18, 40 };
            Row h = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(entete);
                sheet.setColumnWidth(i, largeurs[i] * 256);
            }

            int r = 1;
            for (DispoProduit p : produits) {
                if (!p.isActif()) { continue; }
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nz(p.getCip7()));
                row.createCell(1).setCellValue(nz(p.getCip13()));
                row.createCell(2).setCellValue(nz(p.getNomProduit()));
                row.createCell(3).setCellValue(nz(p.getStatut()));
                row.createCell(4).setCellValue(nz(p.getAgence()));
                if (p.getQuantiteDisponible() != null) { row.createCell(5).setCellValue(p.getQuantiteDisponible()); }
                if (p.getSeuilRupture() != null) { row.createCell(6).setCellValue(p.getSeuilRupture()); }
                row.createCell(7).setCellValue(p.getDatePeremption() != null ? p.getDatePeremption().format(DF) : "");
                row.createCell(8).setCellValue(nz(p.getLienReservation()));
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Génération du bulletin impossible : " + e.getMessage(), e);
        }
    }

    private String nz(String s) { return s == null ? "" : s; }
}
