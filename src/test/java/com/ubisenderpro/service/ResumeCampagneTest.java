package com.ubisenderpro.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Description de la campagne generee a la validation d'une proposition.
 *
 * <p>Le corps du message y etait recopie integralement : le champ
 * "Description" du formulaire devenait illisible, et un message de promotion
 * un peu long faisait echouer l'insertion. On y met desormais un resume court,
 * le message restant dans le modele associe.</p>
 */
class ResumeCampagneTest {

    @Test
    void resumeCourtEtInformatif() {
        String r = EnvoiProposeService.resumeCampagne("Promotion", "Offre AKAZ");
        assertTrue(r.startsWith("Promotion"), r);
        assertTrue(r.contains("Offre AKAZ"), "Le titre doit etre rappele : " + r);
        assertTrue(r.contains("mod\u00e8le"), "L'utilisateur doit savoir ou est le message : " + r);
        assertTrue(r.length() <= 500, "Le resume doit rester court : " + r.length());
    }

    @Test
    void titreAbsentTolere() {
        assertDoesNotThrow(() -> EnvoiProposeService.resumeCampagne("Information", null));
        String r = EnvoiProposeService.resumeCampagne("Information", "   ");
        assertTrue(r.startsWith("Information"), r);
    }

    @Test
    void objectifAbsentDonneUnLibelleParDefaut() {
        assertTrue(EnvoiProposeService.resumeCampagne(null, "X").startsWith("Envoi"));
        assertTrue(EnvoiProposeService.resumeCampagne("  ", "X").startsWith("Envoi"));
    }

    @Test
    void titreTresLongNeDepassePasLaLimite() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) { sb.append("titre-tres-long "); }
        String r = EnvoiProposeService.resumeCampagne("Promotion", sb.toString());
        assertTrue(r.length() <= 500, "Longueur obtenue : " + r.length());
    }
}
