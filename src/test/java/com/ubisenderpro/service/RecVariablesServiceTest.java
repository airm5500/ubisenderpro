package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Personnalisation des modèles de relance : les variables {var} et {{var}} sont
 * remplacées, et toute variable non reconnue est nettoyée (jamais affichée telle
 * quelle au client).
 */
class RecVariablesServiceTest {

    private final RecVariablesService service = new RecVariablesService();

    @Test
    void personnaliser_remplaceLesDeuxFormats() {
        Map<String, String> vars = new HashMap<>();
        vars.put("nom_client", "Pharmacie du Plateau");
        vars.put("montant_du", "150 000");

        String corps = "Bonjour {nom_client}, votre solde est de {{montant_du}} FCFA.";
        String out = service.personnaliser(corps, vars);

        assertEquals("Bonjour Pharmacie du Plateau, votre solde est de 150 000 FCFA.", out);
    }

    @Test
    void personnaliser_nettoieLesVariablesInconnues() {
        Map<String, String> vars = new HashMap<>();
        vars.put("nom_client", "Awa");

        String out = service.personnaliser("Bonjour {nom_client} {inconnu} {{autre}}", vars);

        assertFalse(out.contains("{"), "aucune accolade ne doit subsister");
        assertFalse(out.contains("inconnu"), "les variables non résolues sont retirées");
    }

    @Test
    void personnaliser_corpsNull_renvoieChaineVide() {
        assertEquals("", service.personnaliser(null, new HashMap<>()));
    }
}
