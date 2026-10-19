package ci.ubipharm.crm.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import ci.ubipharm.crm.rest.service.ParametreService;
import ci.ubipharm.crm.rest.service.dto.TableurDTO;
import ci.ubipharm.crm.socle.erreur.ErreurMetierException;

class LecteurTableurServiceImplTest {

    private final ParametreService parametreService = mock(ParametreService.class);
    private final LecteurTableurServiceImpl lecteur = new LecteurTableurServiceImpl(parametreService);

    @BeforeEach
    void limite() {
        when(parametreService.entier(eq("import.lignes_max"), anyInt())).thenReturn(3);
    }

    @Test
    void unCsvWindowsAvecPointVirguleEtGuillemetsEstLu() {
        String csv = "Raison sociale;Date d'échéance;Montant\r\n\"Pharmacie \"\"Le Bon\"\"; Yop\";30/09/2026;150 000\r\n;;\r\n";
        TableurDTO tableur = lecteur.lire(new MockMultipartFile("fichier", "f.csv", "text/csv", csv.getBytes(Charset.forName("windows-1252"))));

        assertThat(tableur.getEntetes()).containsExactly("raison_sociale", "date_d_echeance", "montant");
        assertThat(tableur.getLignes()).hasSize(1);
        assertThat(tableur.getLignes().getFirst().getNumero()).isEqualTo(2);
        assertThat(tableur.getLignes().getFirst().getValeurs())
                .containsEntry("raison_sociale", "Pharmacie \"Le Bon\"; Yop")
                .containsEntry("montant", "150 000");
    }

    @Test
    void unCsvUtf8AvecBomEtVirgulesEstLu() {
        String csv = "﻿code,ville\nC1,Abidjan\n";
        TableurDTO tableur = lecteur.lire(new MockMultipartFile("fichier", "f.CSV", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(tableur.getEntetes()).containsExactly("code", "ville");
        assertThat(tableur.getLignes().getFirst().getValeurs()).containsEntry("ville", "Abidjan");
    }

    @Test
    void unClasseurExcelRendLesDatesEnIsoEtLesNombresSansDecimalesInutiles() throws Exception {
        byte[] contenu;
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet();
            Row entetes = feuille.createRow(0);
            entetes.createCell(0).setCellValue("Date");
            entetes.createCell(1).setCellValue("Montant");
            entetes.createCell(2).setCellValue("Code");
            Row ligne = feuille.createRow(1);
            CellStyle style = classeur.createCellStyle();
            style.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            ligne.createCell(0).setCellValue(LocalDate.of(2026, 9, 30));
            ligne.getCell(0).setCellStyle(style);
            ligne.createCell(1).setCellValue(150000.0);
            ligne.createCell(2).setCellValue(1234);
            classeur.write(sortie);
            contenu = sortie.toByteArray();
        }
        TableurDTO tableur = lecteur.lire(new MockMultipartFile("fichier", "f.xlsx", null, contenu));
        assertThat(tableur.getLignes().getFirst().getValeurs())
                .containsEntry("date", "2026-09-30")
                .containsEntry("montant", "150000")
                .containsEntry("code", "1234");
    }

    @Test
    void unFichierTropLongOuDansUnAutreFormatEstRefuse() {
        String csv = "code\nA\nB\nC\nD\n";
        assertThatThrownBy(() -> lecteur.lire(new MockMultipartFile("fichier", "f.csv", null, csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ErreurMetierException.class)
                .hasMessageContaining("dépasse 3 lignes");
        assertThatThrownBy(() -> lecteur.lire(new MockMultipartFile("fichier", "f.pdf", null, new byte[] {1})))
                .isInstanceOf(ErreurMetierException.class)
                .hasMessageContaining("Format non pris en charge");
        assertThatThrownBy(() -> lecteur.lire(new MockMultipartFile("fichier", "f.xlsx", null, new byte[] {1, 2, 3})))
                .isInstanceOf(ErreurMetierException.class)
                .hasMessageContaining("n'a pas pu être lu");
    }
}
