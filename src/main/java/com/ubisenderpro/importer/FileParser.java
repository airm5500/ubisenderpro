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

    /** Vrai si le nom de fichier désigne un classeur Excel. */
    public static boolean estExcel(String nomFichier) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase();
        return nom.endsWith(".xlsx") || nom.endsWith(".xls");
    }

    /**
     * En-têtes du fichier, dans l'ordre des colonnes — y compris lorsque le
     * fichier ne contient aucune ligne de données. Sert à l'assistant d'import,
     * qui doit proposer les colonnes réelles du fichier et non les faire saisir.
     */
    public static List<String> entetes(byte[] contenu, String nomFichier, char separateur) throws Exception {
        if (estExcel(nomFichier)) {
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
                Sheet sheet = wb.getSheetAt(0);
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                List<String> headers = new ArrayList<>();
                if (headerRow != null) {
                    DataFormatter formatter = new DataFormatter();
                    for (Cell cell : headerRow) {
                        headers.add(formatter.formatCellValue(cell).trim());
                    }
                }
                return headers;
            }
        }
        try (Reader reader = new java.io.StringReader(decoderTexte(contenu));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(separateur)
                     .withFirstRecordAsHeader()
                     .withIgnoreEmptyLines()
                     .withTrim())) {
            return new ArrayList<>(parser.getHeaderNames());
        }
    }

    /**
     * Aperçu d'un fichier : colonnes détectées, quelques lignes d'exemple et
     * nombre total de lignes. Permet à l'utilisateur de reconnaître ses colonnes
     * à leur contenu, et pas seulement à leur intitulé.
     */
    public static Map<String, Object> apercu(byte[] contenu, String nomFichier, char separateur,
                                             int nbLignesExemple) throws Exception {
        List<String> colonnes = entetes(contenu, nomFichier, separateur);
        List<Map<String, String>> toutes = parse(contenu, nomFichier, separateur);
        List<Map<String, String>> exemples = new ArrayList<>();
        for (int i = 0; i < toutes.size() && i < nbLignesExemple; i++) {
            exemples.add(toutes.get(i));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("colonnes", colonnes);
        r.put("exemples", exemples);
        r.put("totalLignes", toutes.size());
        return r;
    }

    /**
     * Décode un CSV en texte avec détection du jeu de caractères : les fichiers
     * exportés d'Excel sous Windows sont souvent en ANSI (windows-1252) et non
     * en UTF-8 — les lire de force en UTF-8 transformait chaque accent en
     * « � » (« Numéro » devenait « Num�ro », faussant aussi le pré-mapping).
     *
     * <ul>
     *   <li>BOM UTF-8 présent → UTF-8 (BOM retiré) ;</li>
     *   <li>sinon décodage UTF-8 STRICT : s'il réussit, c'est de l'UTF-8
     *       (un fichier windows-1252 accentué n'est presque jamais de l'UTF-8
     *       valide) ;</li>
     *   <li>sinon windows-1252, où tout octet est valide.</li>
     * </ul>
     */
    static String decoderTexte(byte[] contenu) {
        if (contenu == null) { return ""; }
        int debut = 0;
        if (contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF
                && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF) {
            debut = 3; // BOM UTF-8
            return new String(contenu, debut, contenu.length - debut, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(contenu)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(contenu, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    private static List<Map<String, String>> parseCsv(byte[] contenu, char separateur) throws Exception {
        List<Map<String, String>> lignes = new ArrayList<>();
        try (Reader reader = new java.io.StringReader(decoderTexte(contenu));
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
