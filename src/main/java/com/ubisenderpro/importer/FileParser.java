package com.ubisenderpro.importer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lit un fichier Excel (.xlsx) ou CSV et le restitue sous forme de lignes
 * indexées par en-tête de colonne. Les valeurs sont toujours rendues en texte
 * (les téléphones ne sont jamais convertis en nombre — section 8.4 de la spec).
 */
public final class FileParser {

    private FileParser() {
    }

    public static List<Map<String, String>> parse(byte[] contenu, String nomFichier, char separateur) throws Exception {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase();
        if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) {
            return parseExcel(contenu);
        }
        return parseCsv(contenu, separateur);
    }

    private static List<Map<String, String>> parseCsv(byte[] contenu, char separateur) throws Exception {
        List<Map<String, String>> lignes = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contenu), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(separateur)
                     .withFirstRecordAsHeader()
                     .withIgnoreEmptyLines()
                     .withTrim())) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                Map<String, String> ligne = new LinkedHashMap<>();
                for (String h : headers) {
                    ligne.put(h, record.isMapped(h) ? record.get(h) : "");
                }
                lignes.add(ligne);
            }
        }
        return lignes;
    }

    private static List<Map<String, String>> parseExcel(byte[] contenu) throws Exception {
        List<Map<String, String>> lignes = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return lignes;
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> ligne = new LinkedHashMap<>();
                boolean vide = true;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String valeur = lireCellule(cell, formatter);
                    if (!valeur.isEmpty()) vide = false;
                    ligne.put(headers.get(c), valeur);
                }
                if (!vide) lignes.add(ligne);
            }
        }
        return lignes;
    }

    /**
     * Lit une cellule en texte. Les nombres entiers sont rendus sans notation
     * scientifique ni décimale afin de préserver les numéros de téléphone.
     */
    private static String lireCellule(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.format("%.0f", d);
            }
        }
        return formatter.formatCellValue(cell).trim();
    }
}
