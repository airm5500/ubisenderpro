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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tous les modeles .jrxml embarques doivent compiler et se remplir : une
 * coquille XML ou une expression fausse n'apparaitrait sinon qu'au premier
 * clic sur « Exporter > PDF » de l'ecran concerne, en production.
 */
class RapportModelesTest {

    private JasperReport compiler(String nom) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/reports/" + nom + ".jrxml")) {
            assertNotNull(in, "reports/" + nom + ".jrxml absent du livrable");
            return JasperCompileManager.compileReport(in);
        }
    }

    private static Map<String, Object> parametres() {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("SOCIETE_NOM", "LABOREX CI");
        p.put("SOCIETE_ADRESSE", "01 BP 1234 Abidjan 01");
        p.put("SOCIETE_TEL", "27 21 00 00 00");
        p.put("SOCIETE_SITE", "https://exemple.ci");
        p.put("LOGO", null);
        p.put("SOUS_TITRE", "");
        return p;
    }

    /** Nommage des archives : menu_ddMMyyyy_HHmmssCC.ext, sans accents ni espaces. */
    @Test
    void nomArchiveEtSlug() {
        assertEquals("comptes_clients", RapportService.slug("Comptes clients"));
        assertEquals("evolution_des_envois_30_jours", RapportService.slug("Évolution des envois (30 jours)"));
        assertEquals("document", RapportService.slug("   "));
        String nom = RapportService.nomArchive("Comptes clients", "pdf");
        org.junit.jupiter.api.Assertions.assertTrue(
                nom.matches("comptes_clients_\\d{8}_\\d{8}\\.pdf"), nom);
    }

    /** Chaque modele de liste compile, se remplit (vide) et exporte un PDF. */
    @Test
    void tousLesModelesDeListeCompilentEtProduisentUnPdf() throws Exception {
        for (String nom : RapportService.MODELES_EMBARQUES) {
            // Le releve (maitre + sous-rapports) a son propre scenario ci-dessous.
            if (nom.startsWith("releve")) { continue; }
            JasperPrint print = JasperFillManager.fillReport(compiler(nom), parametres(),
                    new JRMapCollectionDataSource(new ArrayList<>()));
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII),
                    "modele " + nom);
        }
    }

    /** Le releve de compte : rapport maitre + deux sous-rapports. */
    @Test
    void releveCompletAvecSousRapports() throws Exception {
        Map<String, Object> p = parametres();
        p.put("CLIENT_NOM", "PHARMACIE DU PLATEAU");
        p.put("CLIENT_NUMERO", "C-0042");
        p.put("CLIENT_AGENCE", "ABIDJAN");
        p.put("ENCOURS_INITIAL", "1 500 000,00");
        p.put("TOTAL_FACTURES", "2 300 000,00");
        p.put("TOTAL_AVOIRS", "100 000,00");
        p.put("TOTAL_PAIEMENTS", "1 800 000,00");
        p.put("SOLDE", "1 900 000,00");

        Map<String, Object> creance = new LinkedHashMap<>();
        creance.put("numero", "F-2026-001");
        creance.put("type", "Facture");
        creance.put("emission", "01/07/2026");
        creance.put("echeance", "31/07/2026");
        creance.put("montant", "2 300 000,00");
        Map<String, Object> paiement = new LinkedHashMap<>();
        paiement.put("date", "15/07/2026");
        paiement.put("mode", "Virement");
        paiement.put("reference", "VIR-778");
        paiement.put("montant", "1 800 000,00");
        p.put("LIGNES_CREANCES", (Collection<?>) Arrays.asList(creance));
        p.put("LIGNES_PAIEMENTS", (Collection<?>) Arrays.asList(paiement));
        p.put("SR_CREANCES", compiler("releve_creances"));
        p.put("SR_PAIEMENTS", compiler("releve_paiements"));

        JasperPrint print = JasperFillManager.fillReport(compiler("releve"), p,
                new JRMapCollectionDataSource(new ArrayList<>()));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, print.getPages().size());
    }

    /** Releve d'un client sans aucune creance ni paiement : mentions « Aucun ». */
    @Test
    void releveVideSansErreur() throws Exception {
        Map<String, Object> p = parametres();
        p.put("CLIENT_NOM", "PHARMACIE TEST");
        p.put("CLIENT_NUMERO", "");
        p.put("CLIENT_AGENCE", "");
        p.put("ENCOURS_INITIAL", "0");
        p.put("TOTAL_FACTURES", "0");
        p.put("TOTAL_AVOIRS", "0");
        p.put("TOTAL_PAIEMENTS", "0");
        p.put("SOLDE", "0");
        p.put("LIGNES_CREANCES", new ArrayList<>());
        p.put("LIGNES_PAIEMENTS", new ArrayList<>());
        p.put("SR_CREANCES", compiler("releve_creances"));
        p.put("SR_PAIEMENTS", compiler("releve_paiements"));

        JasperPrint print = JasperFillManager.fillReport(compiler("releve"), p,
                new JRMapCollectionDataSource(new ArrayList<>()));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
