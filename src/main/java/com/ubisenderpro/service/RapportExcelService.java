package com.ubisenderpro.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Export Excel (.xlsx) générique des listes de l'application : l'écran envoie
 * ses colonnes (clé + libellé) et ses lignes telles qu'affichées, le serveur
 * construit un vrai classeur — en-tête société, bandeau de colonnes, filtre
 * automatique — et en archive une copie (répertoire d'archivage, sous-dossier
 * {@code excel}) pour consultation ultérieure sans réexport.
 */
@Stateless
public class RapportExcelService {

    @EJB
    private ParametreService parametreService;
    @EJB
    private RapportService rapportService;

    /**
     * Construit le classeur et l'archive.
     *
     * @param titre    intitulé du menu (1re ligne du classeur + nom d'archive)
     * @param colonnes paires {d: clé, t: libellé}, dans l'ordre d'affichage
     * @param lignes   valeurs par clé, déjà mises en forme par l'écran
     */
    public byte[] xlsx(String titre, List<Map<String, String>> colonnes, List<Map<String, ?>> lignes) {
        if (colonnes == null || colonnes.isEmpty()) {
            throw new ValidationException("colonnes", "Aucune colonne à exporter.");
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet(nomFeuille(titre));

            Font fTitre = wb.createFont();
            fTitre.setBold(true);
            fTitre.setFontHeightInPoints((short) 13);
            fTitre.setColor(IndexedColors.DARK_BLUE.getIndex());
            CellStyle sTitre = wb.createCellStyle();
            sTitre.setFont(fTitre);

            Font fEntete = wb.createFont();
            fEntete.setBold(true);
            fEntete.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle sEntete = wb.createCellStyle();
            sEntete.setFont(fEntete);
            sEntete.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x1F, (byte) 0x4E, (byte) 0x79}, null));
            sEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sEntete.setAlignment(HorizontalAlignment.LEFT);
            sEntete.setBorderBottom(BorderStyle.THIN);

            CellStyle sCellule = wb.createCellStyle();
            sCellule.setBorderBottom(BorderStyle.HAIR);

            // Ligne 1 : société + menu ; ligne 2 : date d'édition ; ligne 4 : en-têtes.
            String societe = parametreService.valeur("app.societe", "");
            Row r0 = sh.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue((societe == null || societe.trim().isEmpty() ? "" : societe.trim() + " — ")
                    + (titre == null || titre.trim().isEmpty() ? "Export" : titre.trim()));
            c0.setCellStyle(sTitre);
            sh.createRow(1).createCell(0).setCellValue("Édité le "
                    + new java.text.SimpleDateFormat("dd/MM/yyyy 'à' HH:mm").format(new java.util.Date()));

            int ligneEntete = 3;
            Row re = sh.createRow(ligneEntete);
            for (int i = 0; i < colonnes.size(); i++) {
                Cell c = re.createCell(i);
                c.setCellValue(colonnes.get(i).getOrDefault("t", colonnes.get(i).get("d")));
                c.setCellStyle(sEntete);
            }
            int r = ligneEntete + 1;
            for (Map<String, ?> l : (lignes == null ? java.util.Collections.<Map<String, ?>>emptyList() : lignes)) {
                Row row = sh.createRow(r++);
                for (int i = 0; i < colonnes.size(); i++) {
                    Object v = l.get(colonnes.get(i).get("d"));
                    Cell c = row.createCell(i);
                    c.setCellValue(v == null ? "" : String.valueOf(v));
                    c.setCellStyle(sCellule);
                }
            }
            // Confort : volet figé sous l'en-tête + filtre automatique + largeurs.
            sh.createFreezePane(0, ligneEntete + 1);
            if (r > ligneEntete + 1) {
                sh.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        ligneEntete, r - 1, 0, colonnes.size() - 1));
            }
            for (int i = 0; i < colonnes.size(); i++) {
                sh.autoSizeColumn(i);
                // autoSizeColumn peut donner des largeurs extrêmes : bornées.
                int w = sh.getColumnWidth(i);
                sh.setColumnWidth(i, Math.max(2200, Math.min(w + 300, 12000)));
            }

            wb.write(out);
            byte[] contenu = out.toByteArray();
            rapportService.archiver("excel", titre, "xlsx", contenu);
            return contenu;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("excel", "Génération du classeur Excel impossible : "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Nom d'onglet Excel : 31 caractères max, sans caractères interdits. */
    private static String nomFeuille(String titre) {
        String s = (titre == null || titre.trim().isEmpty()) ? "Export" : titre.trim();
        s = s.replaceAll("[\\\\/*?\\[\\]:]", " ");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
