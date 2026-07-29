package com.ubisenderpro.importer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection des colonnes d'un fichier a importer.
 *
 * <p>L'assistant d'import n'analysait le fichier que dans le navigateur, et
 * seulement au format CSV : pour un classeur Excel il demandait a l'utilisateur
 * de SAISIR lui-meme les noms de colonnes. L'analyse est desormais faite par le
 * serveur, qui lit les deux formats.</p>
 */
class FileParserApercuTest {

    private static byte[] csv(String contenu) {
        return contenu.getBytes(StandardCharsets.UTF_8);
    }

    /** Classeur .xlsx minimal : une ligne d'en-tetes puis des lignes de donnees. */
    private static byte[] xlsx(String[] entetes, String[][] lignes) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Clients");
            Row h = sh.createRow(0);
            for (int i = 0; i < entetes.length; i++) { h.createCell(i).setCellValue(entetes[i]); }
            for (int r = 0; r < lignes.length; r++) {
                Row row = sh.createRow(r + 1);
                for (int c = 0; c < lignes[r].length; c++) { row.createCell(c).setCellValue(lignes[r][c]); }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void entetesCsv() throws Exception {
        List<String> cols = FileParser.entetes(
                csv("code;nom;telephone\nC001;PHCIE POLAP;2250700000000\n"), "clients.csv", ';');
        assertEquals(java.util.Arrays.asList("code", "nom", "telephone"), cols);
    }

    /** Le separateur choisi est bien celui applique (virgule et non point-virgule). */
    @Test
    void entetesCsvSeparateurVirgule() throws Exception {
        List<String> cols = FileParser.entetes(csv("code,nom\nC001,POLAP\n"), "clients.csv", ',');
        assertEquals(java.util.Arrays.asList("code", "nom"), cols);
    }

    /** Un fichier sans aucune ligne de donnees expose quand meme ses colonnes. */
    @Test
    void entetesCsvSansDonnees() throws Exception {
        assertEquals(java.util.Arrays.asList("code", "nom"),
                FileParser.entetes(csv("code;nom\n"), "clients.csv", ';'));
    }

    @Test
    void entetesExcel() throws Exception {
        byte[] f = xlsx(new String[]{"CODE PS", "PHARMACIE", "TEL 1"},
                new String[][]{{"C001", "POLAP", "2250700000000"}});
        assertEquals(java.util.Arrays.asList("CODE PS", "PHARMACIE", "TEL 1"),
                FileParser.entetes(f, "clients.xlsx", ';'));
    }

    @Test
    void apercuExcelDonneColonnesExemplesEtTotal() throws Exception {
        byte[] f = xlsx(new String[]{"CODE PS", "PHARMACIE"},
                new String[][]{{"C001", "POLAP"}, {"C002", "SAINT JEAN"}, {"C003", "PLATEAU"},
                               {"C004", "COCODY"}});
        Map<String, Object> a = FileParser.apercu(f, "clients.xlsx", ';', 3);

        assertEquals(java.util.Arrays.asList("CODE PS", "PHARMACIE"), a.get("colonnes"));
        assertEquals(4, a.get("totalLignes"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> exemples = (List<Map<String, String>>) a.get("exemples");
        // Trois lignes d'exemple au plus, meme si le fichier en compte davantage.
        assertEquals(3, exemples.size());
        assertEquals("C001", exemples.get(0).get("CODE PS"));
        assertEquals("PLATEAU", exemples.get(2).get("PHARMACIE"));
    }

    @Test
    void apercuCsvMoinsDeLignesQueDemande() throws Exception {
        Map<String, Object> a = FileParser.apercu(csv("code;nom\nC001;POLAP\n"), "clients.csv", ';', 3);
        assertEquals(1, a.get("totalLignes"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> exemples = (List<Map<String, String>>) a.get("exemples");
        assertEquals(1, exemples.size());
    }

    @Test
    void reconnaissanceDuFormatExcel() {
        assertTrue(FileParser.estExcel("liste.xlsx"));
        assertTrue(FileParser.estExcel("LISTE.XLS"));
        assertTrue(!FileParser.estExcel("liste.csv"));
        assertTrue(!FileParser.estExcel(null));
    }
}
