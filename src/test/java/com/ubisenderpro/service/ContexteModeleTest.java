package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Variables de contexte figees sur un modele (liste de produits, agence, dates).
 *
 * <p>Elles portent le contenu METIER du message. Sur le canal WhatsApp Web,
 * elles n'etaient pas appliquees : restees non resolues, elles etaient ensuite
 * effacees par le filet anti-variable de la personnalisation, et le bloc
 * produit disparaissait du message recu - le reste du texte arrivant
 * normalement.</p>
 */
class ContexteModeleTest {

    private Map<String, String> contexte() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("liste_produits", "\u2705 DOLIPRANE\n   Peremption : 08/2026");
        v.put("agence", "ABIDJAN");
        v.put("nombre_produits", "1");
        return v;
    }

    @Test
    void listeDeProduitsInseree() {
        String corps = "Produits disponibles :\n{{liste_produits}}\nA bientot !";
        String out = CampagneSenderTx.appliquerContexte(corps, contexte());

        assertTrue(out.contains("DOLIPRANE"), "Le produit doit apparaitre : " + out);
        assertTrue(out.contains("08/2026"), "La peremption doit apparaitre : " + out);
        assertFalse(out.contains("{{liste_produits}}"), "Le jeton ne doit plus subsister");
        assertTrue(out.contains("A bientot !"), "Le reste du message est preserve");
    }

    @Test
    void variableNonResolueSeraitEffacee() {
        // Reproduit la cause du defaut : sans application du contexte, le jeton
        // survit ici puis disparait au nettoyage final de la personnalisation.
        String corps = "Produits :\n{{liste_produits}}";
        String sansContexte = CampagneSenderTx.appliquerContexte(corps, new LinkedHashMap<>());
        assertTrue(sansContexte.contains("{{liste_produits}}"),
                "Sans contexte, le jeton reste - c'est le nettoyage suivant qui l'effacait");
    }

    @Test
    void lesDeuxEcrituresSontAcceptees() {
        String corps = "Agence {{agence}} / secours [AGENCE]";
        String out = CampagneSenderTx.appliquerContexte(corps, contexte());
        assertEquals("Agence ABIDJAN / secours ABIDJAN", out);
    }

    @Test
    void casseDeLaCleToleree() {
        String out = CampagneSenderTx.appliquerContexte("{{LISTE_PRODUITS}}", contexte());
        assertTrue(out.contains("DOLIPRANE"), out);
    }

    @Test
    void valeurNulleDonneUneChaineVide() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("lien_reservation", null);
        assertEquals("Lien : ", CampagneSenderTx.appliquerContexte("Lien : {{lien_reservation}}", v));
    }

    @Test
    void corpsOuContexteAbsentNeCassePas() {
        assertNull(CampagneSenderTx.appliquerContexte(null, contexte()));
        assertEquals("Texte", CampagneSenderTx.appliquerContexte("Texte", null));
        assertEquals("Texte", CampagneSenderTx.appliquerContexte("Texte", new LinkedHashMap<>()));
    }
}
