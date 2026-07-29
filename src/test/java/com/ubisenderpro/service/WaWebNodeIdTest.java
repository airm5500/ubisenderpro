package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identifiant de session cote service Node.
 *
 * <p>Le service Node nomme ses sessions {@code acc-<id>}. Les ecrans qui
 * enregistrent l'identifiant sous forme de texte (ciblage d'une campagne)
 * stockent la valeur brute (« 2 ») : sans conversion, l'appel visait une
 * session inexistante et tous les envois echouaient en « Session non
 * connectee » alors que la session etait bien connectee.</p>
 */
class WaWebNodeIdTest {

    @Test
    void identifiantNumeriquePrefixe() {
        assertEquals("acc-2", WaWebSessionService.nodeId(2L));
        assertEquals("acc-2", WaWebSessionService.nodeIdDepuisTexte("2"));
    }

    @Test
    void valeurDejaPrefixeeNonDoublee() {
        assertEquals("acc-2", WaWebSessionService.nodeIdDepuisTexte("acc-2"),
                "Une valeur deja prefixee ne doit pas devenir acc-acc-2");
    }

    @Test
    void espacesToleres() {
        assertEquals("acc-7", WaWebSessionService.nodeIdDepuisTexte("  7 "));
        assertEquals("acc-7", WaWebSessionService.nodeIdDepuisTexte(" acc-7 "));
    }

    @Test
    void valeurAbsenteDonneNull() {
        assertNull(WaWebSessionService.nodeIdDepuisTexte(null));
        assertNull(WaWebSessionService.nodeIdDepuisTexte(""));
        assertNull(WaWebSessionService.nodeIdDepuisTexte("   "));
    }

    @Test
    void coherenceEntreLesDeuxFormes() {
        // Les deux chemins (envoi unitaire par id numerique, campagne par texte)
        // doivent viser exactement la meme session.
        for (long id : new long[]{1L, 2L, 42L}) {
            assertEquals(WaWebSessionService.nodeId(id),
                    WaWebSessionService.nodeIdDepuisTexte(String.valueOf(id)),
                    "Les deux formes doivent produire le meme identifiant");
        }
    }
}
