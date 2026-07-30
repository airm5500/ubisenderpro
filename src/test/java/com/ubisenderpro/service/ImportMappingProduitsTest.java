package com.ubisenderpro.service;

import com.ubisenderpro.entity.Promotion;
import com.ubisenderpro.entity.PromotionProduit;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Import des produits d'une promotion avec correspondance EXPLICITE des
 * colonnes (assistant) : les colonnes sont resolues par l'intitule choisi par
 * l'utilisateur, plus par heuristique — un fichier aux intitules exotiques
 * (« REF 7 », « Designation article ») s'importe correctement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportMappingProduitsTest {

    @Mock
    private EntityManager em;
    @Mock
    private PromotionService promotionService;
    @Mock
    private ArticleService articleService;

    @InjectMocks
    private PromotionProduitService service;

    private static byte[] xlsx(String[] entetes, String[][] lignes) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Produits");
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

    @SuppressWarnings("unchecked")
    private void promotionExistante() {
        when(em.find(eq(Promotion.class), any())).thenReturn(new Promotion());
        TypedQuery<PromotionProduit> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(Collections.emptyList());
        when(em.createQuery(anyString(), eq(PromotionProduit.class))).thenReturn(q);
    }

    @Test
    void mappingExpliciteResoutDesIntitulesExotiques() throws Exception {
        promotionExistante();
        // « REF 7 » ne contient pas « cip7 » : l'heuristique historique ne
        // l'aurait jamais trouvé. La colonne CODE INTERNE doit être IGNORÉE.
        byte[] f = xlsx(new String[]{"CODE INTERNE", "REF 7", "Désignation article"},
                new String[][]{{"X-1", "1234567", "DOLIPRANE 500"},
                               {"X-2", "7654321", "EFFERALGAN"}});
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("cip7", "REF 7");
        mapping.put("nom", "Désignation article");

        Map<String, Object> r = service.importer(42L, f, mapping);

        assertEquals(2, r.get("crees"));
        ArgumentCaptor<PromotionProduit> cap = ArgumentCaptor.forClass(PromotionProduit.class);
        verify(em, org.mockito.Mockito.times(2)).persist(cap.capture());
        assertEquals("1234567", cap.getAllValues().get(0).getCip7());
        assertEquals("DOLIPRANE 500", cap.getAllValues().get(0).getNomProduit());
        // Champ non fourni dans la correspondance : jamais lu, même si une
        // colonne du fichier aurait pu correspondre par heuristique.
        assertEquals("", nz(cap.getAllValues().get(0).getCip13()));
    }

    /** Sans mapping, le comportement historique (heuristique) est inchangé. */
    @Test
    void sansMappingHeuristiqueConservee() throws Exception {
        promotionExistante();
        byte[] f = xlsx(new String[]{"CIP7", "CIP13", "Produit"},
                new String[][]{{"1234567", "", "DOLIPRANE 500"}});
        Map<String, Object> r = service.importer(42L, f, null);
        assertEquals(1, r.get("crees"));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
