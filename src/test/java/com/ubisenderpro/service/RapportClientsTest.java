package com.ubisenderpro.service;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le modele embarque reports/clients.jrxml doit compiler et produire un PDF.
 *
 * <p>Un .jrxml est du XML compile a l'execution : une coquille (balise,
 * expression Java, champ renomme) n'apparaitrait qu'au premier clic sur
 * « Exporter > PDF » en production. Ce test compile et remplit le modele
 * exactement comme RapportService, donnees comprises.</p>
 */
class RapportClientsTest {

    private static Map<String, Object> parametres() {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("SOCIETE_NOM", "LABOREX CI");
        p.put("SOCIETE_ADRESSE", "01 BP 1234 Abidjan 01");
        p.put("SOCIETE_TEL", "27 21 00 00 00");
        p.put("SOCIETE_SITE", "https://exemple.ci");
        p.put("LOGO", null);
        p.put("TITRE", "Comptes clients");
        p.put("SOUS_TITRE", "Filtres : Segmentation Gold");
        return p;
    }

    private static Map<String, ?> ligne(String code, String nom) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("code", code);
        l.put("nom", nom);
        l.put("entreprise", "PHCIE " + nom);
        l.put("telephone", "2250700000000");
        l.put("email", nom.toLowerCase(Locale.FRANCE) + "@exemple.ci");
        l.put("segmentation", "Gold");
        l.put("agence", "ABIDJAN");
        l.put("region", "LAGUNES");
        l.put("tournee", "T1");
        return l;
    }

    private JasperReport compiler() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/reports/clients.jrxml")) {
            assertNotNull(in, "reports/clients.jrxml absent du livrable");
            return JasperCompileManager.compileReport(in);
        }
    }

    @Test
    void leModeleCompileEtProduitUnPdf() throws Exception {
        Collection<Map<String, ?>> lignes = new ArrayList<>();
        for (int i = 1; i <= 60; i++) { lignes.add(ligne("C-" + i, "PLATEAU" + i)); }

        JasperPrint print = JasperFillManager.fillReport(compiler(), parametres(),
                new JRMapCollectionDataSource(lignes));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf.length > 1000, "PDF anormalement petit");
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        // 60 lignes en paysage A4 : l'edition pagine (en-tetes repetes).
        assertTrue(print.getPages().size() >= 2, "la pagination ne fonctionne pas");
    }

    /** Sans aucune ligne, l'edition sort quand meme (en-tete + mention vide). */
    @Test
    void editionVideSansErreur() throws Exception {
        JasperPrint print = JasperFillManager.fillReport(compiler(), parametres(),
                new JRMapCollectionDataSource(new ArrayList<>()));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, print.getPages().size());
    }
}
