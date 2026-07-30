package com.ubisenderpro.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tolerance de JSON-B sur les dates envoyees par les ecrans.
 *
 * <p>Payara donne la priorite a JSON-B (Yasson) sur Jackson pour lire les corps
 * de requete : la configuration « chaine vide -> null » d'ObjectMapperProvider
 * ne s'appliquait donc pas. Un champ date laisse vide arrivait sous la forme ""
 * et faisait echouer l'enregistrement avec une erreur technique (500) avant
 * meme la validation metier.</p>
 */
class JsonbProviderTest {

    @Test
    void chaineVideDonneNul() {
        assertNull(JsonbProvider.versDate(""));
        assertNull(JsonbProvider.versDate("   "));
        assertNull(JsonbProvider.versDate(null));
        assertNull(JsonbProvider.versDateHeure(""));
        assertNull(JsonbProvider.versDateHeure("  "));
        assertNull(JsonbProvider.versDateHeure(null));
        assertNull(JsonbProvider.versHeure(""));
        assertNull(JsonbProvider.versHeure(null));
    }

    @Test
    void dateIsoLue() {
        assertEquals(LocalDate.of(2026, 7, 29), JsonbProvider.versDate("2026-07-29"));
        assertEquals(LocalDate.of(2026, 7, 29), JsonbProvider.versDate(" 2026-07-29 "));
    }

    /** L'ecran peut soumettre une date+heure la ou l'entite attend une date seule. */
    @Test
    void dateHeureAccepteePourUneDateSeule() {
        assertEquals(LocalDate.of(2026, 7, 29), JsonbProvider.versDate("2026-07-29T08:30:00"));
    }

    @Test
    void dateHeureIsoLue() {
        assertEquals(LocalDateTime.of(2026, 7, 29, 8, 30, 0),
                JsonbProvider.versDateHeure("2026-07-29T08:30:00"));
    }

    /** Inversement, une date seule pour un champ date+heure vaut minuit. */
    @Test
    void dateSeuleAccepteePourUneDateHeure() {
        assertEquals(LocalDateTime.of(2026, 7, 29, 0, 0),
                JsonbProvider.versDateHeure("2026-07-29"));
    }

    @Test
    void heureLue() {
        assertEquals(LocalTime.of(8, 30), JsonbProvider.versHeure("08:30"));
        assertEquals(LocalTime.of(8, 30, 15), JsonbProvider.versHeure("08:30:15"));
    }

    /** Une vraie erreur de saisie reste une erreur : elle ne doit pas etre avalee. */
    @Test
    void dateInvalideRejetee() {
        assertThrows(DateTimeParseException.class, () -> JsonbProvider.versDate("29/07/2026"));
    }
}
