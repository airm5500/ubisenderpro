package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tickets de consultation des documents generes : usage unique, introuvable
 * une fois consommes — c'est toute leur securite (l'endpoint de vue n'exige
 * pas d'en-tete d'authentification).
 */
class TicketConsultationTest {

    @Test
    void allerRetourEtUsageUnique() {
        byte[] contenu = "%PDF-fake".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String jeton = RapportService.creerTicket("comptes_clients_30072026_10000000.pdf", contenu);
        assertNotNull(jeton);

        Object[] t = RapportService.consommerTicket(jeton);
        assertNotNull(t);
        assertArrayEquals(contenu, (byte[]) t[0]);
        assertEquals("comptes_clients_30072026_10000000.pdf", t[1]);

        // Deja consomme : le meme jeton ne sert qu'une fois.
        assertNull(RapportService.consommerTicket(jeton));
    }

    @Test
    void jetonInconnuOuNul() {
        assertNull(RapportService.consommerTicket("n-existe-pas"));
        assertNull(RapportService.consommerTicket(null));
    }
}
