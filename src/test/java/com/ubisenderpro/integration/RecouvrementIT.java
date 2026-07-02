package com.ubisenderpro.integration;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecFiche;
import com.ubisenderpro.entity.RecPaiement;
import com.ubisenderpro.entity.RecPromesse;
import com.ubisenderpro.service.RecCreanceService;
import com.ubisenderpro.service.RecFicheService;
import com.ubisenderpro.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.ubisenderpro.integration.EnvironnementIntegration.dansTransaction;
import static com.ubisenderpro.integration.EnvironnementIntegration.injecter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration du socle financier Recouvrement contre une vraie MariaDB
 * migrée par Flyway : calcul du solde (encours initial + factures − avoirs −
 * règlements), unicité de la fiche par client, valeurs par défaut et grille
 * avec filtres.
 */
class RecouvrementIT {

    private EntityManager em;
    private RecFicheService fiches;
    private RecCreanceService creances;

    @BeforeEach
    void preparer() {
        em = EnvironnementIntegration.emf().createEntityManager();
        fiches = injecter(new RecFicheService(), em);
        creances = injecter(new RecCreanceService(), em);
    }

    @AfterEach
    void fermer() {
        if (em.getTransaction().isActive()) { em.getTransaction().rollback(); }
        em.close();
    }

    /* --------------------------------- Aides --------------------------------- */

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Client nouveauClient(String nom, String agence) {
        Client c = new Client();
        c.setNumeroClient("IT-" + SEQ.getAndIncrement());
        c.setNomCompte(nom);
        c.setAgence(agence);
        dansTransaction(em, () -> em.persist(c));
        return c;
    }

    private RecFiche nouvelleFiche(Long clientId, BigDecimal encours) {
        RecFiche f = new RecFiche();
        f.setClientId(clientId);
        f.setEncoursInitial(encours);
        dansTransaction(em, () -> fiches.creer(f));
        return f;
    }

    private void nouvelleCreance(Long clientId, String type, String montant) {
        RecCreance c = new RecCreance();
        c.setClientId(clientId);
        c.setType(type);
        c.setMontant(new BigDecimal(montant));
        dansTransaction(em, () -> creances.creerCreance(c));
    }

    private void nouveauPaiement(Long clientId, String montant) {
        RecPaiement p = new RecPaiement();
        p.setClientId(clientId);
        p.setMontant(new BigDecimal(montant));
        dansTransaction(em, () -> creances.creerPaiement(p));
    }

    private static void assertMontant(String attendu, Object reel, String message) {
        assertTrue(reel instanceof BigDecimal, message + " : montant absent");
        assertEquals(0, new BigDecimal(attendu).compareTo((BigDecimal) reel),
                message + " (attendu " + attendu + ", obtenu " + reel + ")");
    }

    /* ------------------------------ Calcul du solde ------------------------------ */

    @Test
    void situation_calculeLeSolde_encoursPlusFacturesMoinsAvoirsMoinsReglements() {
        Client client = nouveauClient("PHARMACIE DU PLATEAU", "Abidjan");
        nouvelleFiche(client.getId(), new BigDecimal("1000.00"));
        nouvelleCreance(client.getId(), "FACTURE", "500.00");
        nouvelleCreance(client.getId(), "FACTURE", "300.00");
        nouvelleCreance(client.getId(), "AVOIR", "200.00");
        nouveauPaiement(client.getId(), "400.00");

        Map<String, Object> s = fiches.situation(client.getId());

        assertMontant("1000.00", s.get("encoursInitial"), "encours initial");
        assertMontant("800.00", s.get("totalFactures"), "total factures");
        assertMontant("200.00", s.get("totalAvoirs"), "total avoirs");
        assertMontant("400.00", s.get("totalPaiements"), "total règlements");
        assertMontant("1200.00", s.get("solde"), "solde = 1000 + 800 − 200 − 400");
        assertMontant("1200.00", fiches.solde(client.getId()), "solde direct");
    }

    @Test
    void situation_clientSansFicheNiMouvement_soldeZero() {
        Client client = nouveauClient("CLIENT SANS FICHE", null);
        assertMontant("0", fiches.solde(client.getId()), "sans fiche ni mouvement, le solde est nul");
    }

    /* --------------------------- Règles d'intégrité --------------------------- */

    @Test
    void uneSeuleFicheParClient() {
        Client client = nouveauClient("CLIENT FIDELE", "Bouake");
        nouvelleFiche(client.getId(), BigDecimal.ZERO);

        RecFiche doublon = new RecFiche();
        doublon.setClientId(client.getId());
        assertThrows(ValidationException.class,
                () -> dansTransaction(em, () -> fiches.creer(doublon)),
                "une deuxième fiche pour le même client doit être refusée");
    }

    @Test
    void creanceSansClientOuSansMontant_refusee() {
        RecCreance sansClient = new RecCreance();
        sansClient.setMontant(BigDecimal.TEN);
        assertThrows(ValidationException.class,
                () -> dansTransaction(em, () -> creances.creerCreance(sansClient)));

        Client client = nouveauClient("CLIENT CONTROLE", null);
        RecCreance sansMontant = new RecCreance();
        sansMontant.setClientId(client.getId());
        sansMontant.setMontant(null); // payload JSON avec « montant »: null
        assertThrows(ValidationException.class,
                () -> dansTransaction(em, () -> creances.creerCreance(sansMontant)));
    }

    /* ----------------------------- Valeurs par défaut ----------------------------- */

    @Test
    void creance_typeParDefaut_estFacture() {
        Client client = nouveauClient("CLIENT DEFAUT", null);
        RecCreance c = new RecCreance();
        c.setClientId(client.getId());
        c.setMontant(new BigDecimal("50.00"));
        dansTransaction(em, () -> creances.creerCreance(c));

        assertEquals("FACTURE", c.getType(), "sans type explicite, une créance est une facture");
    }

    @Test
    void promesse_statutParDefaut_enAttente() {
        Client client = nouveauClient("CLIENT PROMESSE", null);
        RecPromesse p = new RecPromesse();
        p.setClientId(client.getId());
        p.setMontant(new BigDecimal("75.00"));
        dansTransaction(em, () -> creances.creerPromesse(p));

        assertEquals("EN_ATTENTE", p.getStatut());
    }

    /* ------------------------------ Grille filtrée ------------------------------ */

    @Test
    void listerAvecSolde_filtreParRechercheEtAgence_etRetourneLeSolde() {
        Client abidjan = nouveauClient("GRILLE ALPHA", "AgenceGrilleA");
        Client bouake = nouveauClient("GRILLE BETA", "AgenceGrilleB");
        nouvelleFiche(abidjan.getId(), new BigDecimal("100.00"));
        nouvelleFiche(bouake.getId(), new BigDecimal("999.00"));
        nouvelleCreance(abidjan.getId(), "FACTURE", "50.00");

        List<Map<String, Object>> grille = fiches.listerAvecSolde("grille alpha", "AgenceGrilleA", null, null);

        assertEquals(1, grille.size(), "seule la fiche de l'agence A doit rester après filtres");
        assertEquals(abidjan.getId(), grille.get(0).get("clientId"));
        assertMontant("150.00", grille.get(0).get("solde"), "solde de la grille (100 + 50)");
    }
}
