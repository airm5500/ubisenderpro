package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Génération des codes par défaut : format PREFIXE-ANNEE-NNNN et incrément
 * jusqu'à obtenir un code libre.
 */
class CodesTest {

    @Test
    void generer_premierCodeLibre() {
        String code = Codes.generer("PAYS", c -> false);
        assertEquals("PAYS-" + Year.now().getValue() + "-0001", code);
    }

    @Test
    void generer_sauteLesCodesDejaPris() {
        int annee = Year.now().getValue();
        Set<String> pris = new HashSet<>();
        pris.add("AG-" + annee + "-0001");
        pris.add("AG-" + annee + "-0002");

        String code = Codes.generer("AG", pris::contains);

        assertEquals("AG-" + annee + "-0003", code);
        assertTrue(code.startsWith("AG-"), "le préfixe doit être conservé");
    }
}
