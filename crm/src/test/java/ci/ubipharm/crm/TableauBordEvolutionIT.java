package ci.ubipharm.crm;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Courbes d'evolution des tableaux de bord : points par jour ou par semaine, perimetre de l'agent, solde echu reconstitue. */
class TableauBordEvolutionIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;

    private final LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
    private MockHttpSession admin;
    private MockHttpSession agent;
    private Long agent2;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent2", "AGENT", "RECOUVREMENT", YOPOUGON);
        Long agent1 = id("crm_utilisateur", "login", "it-agent1");
        agent2 = id("crm_utilisateur", "login", "it-agent2");
        Long adminId = id("crm_utilisateur", "login", "admin");
        Long c1 = client("E-C1");
        Long c2 = client("E-C2");
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c1, agent1, adminId);
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c2, agent2, adminId);

        // F1 connue depuis 20 jours, echue, 80 000 ramenes a 50 000 il y a 3 jours ; F2 connue depuis 2 jours, echue, 10 000
        Long f1 = facture("E-F1", c1, aujourdhui.minusDays(15), "50000.00", aujourdhui.minusDays(20).atTime(9, 0));
        facture("E-F2", c1, aujourdhui.minusDays(5), "10000.00", aujourdhui.minusDays(2).atTime(9, 0));
        jdbc.update("insert into crm_facture_solde (facture_id, ancien_solde, nouveau_solde, created_at) values (?, 80000, 50000, ?)",
                f1, aujourdhui.minusDays(3).atTime(10, 0));

        Long modele = modele("RECOUVREMENT", adminId);
        Long relance = campagne("RECOUVREMENT", modele, adminId);
        LocalDateTime hier = aujourdhui.minusDays(1).atTime(11, 0);
        message(relance, c1, agent1, "LU", hier);
        message(relance, c1, agent1, "DISTRIBUE", hier);
        message(relance, c2, agent2, "ENVOYE", hier);
        message(relance, c2, agent2, "ECHEC", hier);

        Long promo = campagne("MARKETING", modele("MARKETING", adminId), adminId);
        Long lu = message(promo, c2, null, "LU", hier);
        jdbc.update("""
                insert into crm_message_recu (numero_agent, numero_client, type, identifiant_whatsapp, recu_le, client_id, campagne_message_id)
                values ('2250700000000', '2250500000000', 'TEXTE', 'wamid.it-evolution', ?, ?, ?)""", hier, c2, lu);
        jdbc.update("update crm_client set desabonne_marketing_le = ? where id = ?", hier, c2);

        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        agent = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void recouvrementParJourAvecSoldeEchuReconstitue() throws Exception {
        mvc.perform(get("/api/recouvrement/tableau-de-bord/evolution").param("du", aujourdhui.minusDays(9).toString())
                .param("au", aujourdhui.toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pas").value("JOUR"))
                .andExpect(jsonPath("$.points", hasSize(10)))
                .andExpect(jsonPath("$.points[0].date").value(aujourdhui.minusDays(9).toString()))
                .andExpect(jsonPath("$.points[0].soldeEchu").value(80000.0))
                .andExpect(jsonPath("$.points[0].montantEncaisse").value(0))
                .andExpect(jsonPath("$.points[6].montantEncaisse").value(30000.0))
                .andExpect(jsonPath("$.points[6].soldeEchu").value(50000.0))
                .andExpect(jsonPath("$.points[7].soldeEchu").value(60000.0))
                .andExpect(jsonPath("$.points[8].envoyes").value(3))
                .andExpect(jsonPath("$.points[8].distribues").value(2))
                .andExpect(jsonPath("$.points[8].lus").value(1))
                .andExpect(jsonPath("$.points[9].envoyes").value(0));
        // L'agent ne voit que ses relances et ses clients
        mvc.perform(get("/api/recouvrement/tableau-de-bord/evolution").param("du", aujourdhui.minusDays(9).toString()).session(agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[8].envoyes").value(2))
                .andExpect(jsonPath("$.points[9].soldeEchu").value(60000.0));
        mvc.perform(get("/api/recouvrement/tableau-de-bord/evolution").param("du", aujourdhui.minusDays(9).toString())
                .param("agentId", agent2.toString()).session(admin))
                .andExpect(jsonPath("$.points[8].envoyes").value(1))
                .andExpect(jsonPath("$.points[9].soldeEchu").value(0));
        // Au-dela de 60 jours : un point par semaine ; les intervalles futurs n'ont pas de solde echu
        mvc.perform(get("/api/recouvrement/tableau-de-bord/evolution").param("du", aujourdhui.minusDays(110).toString())
                .param("au", aujourdhui.plusDays(9).toString()).session(admin))
                .andExpect(jsonPath("$.pas").value("SEMAINE"))
                .andExpect(jsonPath("$.points", hasSize(18)))
                .andExpect(jsonPath("$.points[15].envoyes").value(3))
                .andExpect(jsonPath("$.points[15].soldeEchu").value(60000.0))
                .andExpect(jsonPath("$.points[17].soldeEchu").doesNotExist());
        mvc.perform(get("/api/recouvrement/tableau-de-bord/evolution").param("du", "2026-10-10").param("au", "2026-10-01").session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketingParJourEtDroits() throws Exception {
        mvc.perform(get("/api/marketing/tableau-de-bord/evolution").param("du", aujourdhui.minusDays(9).toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(10)))
                .andExpect(jsonPath("$.points[8].envoyes").value(1))
                .andExpect(jsonPath("$.points[8].lus").value(1))
                .andExpect(jsonPath("$.points[8].reponses").value(1))
                .andExpect(jsonPath("$.points[8].desabonnements").value(1))
                .andExpect(jsonPath("$.points[7].envoyes").value(0));
        mvc.perform(get("/api/marketing/tableau-de-bord/evolution").session(agent)).andExpect(status().isForbidden());
    }

    @Test
    void historiqueDesEnvoisParJourAvecLesFiltres() throws Exception {
        mvc.perform(get("/api/historique-envois/evolution").param("du", aujourdhui.minusDays(9).toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pas").value("JOUR"))
                .andExpect(jsonPath("$.points", hasSize(10)))
                .andExpect(jsonPath("$.points[8].envoyes").value(4))
                .andExpect(jsonPath("$.points[8].lus").value(2))
                .andExpect(jsonPath("$.points[8].echecs").value(1));
        mvc.perform(get("/api/historique-envois/evolution").param("du", aujourdhui.minusDays(9).toString()).param("origine", "PROMOTION").session(admin))
                .andExpect(jsonPath("$.points[8].envoyes").value(1))
                .andExpect(jsonPath("$.points[8].echecs").value(0));
        mvc.perform(get("/api/historique-envois/evolution").param("du", "2024-01-01").session(admin))
                .andExpect(jsonPath("$.pas").value("SEMAINE"))
                .andExpect(jsonPath("$.du").value(aujourdhui.minusDays(365).toString()));
    }

    private Long client(String code) {
        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, agence_id, created_at) values (?, ?, ?, ?, utc_timestamp())",
                code, "Pharmacie " + code, "Dr " + code, YOPOUGON);
        return id("crm_client", "code", code);
    }

    private Long facture(String reference, Long clientId, LocalDate echeance, String solde, LocalDateTime creeLe) {
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, statut, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, 80000, ?, 'A_RELANCER', ?)
                """, reference, clientId, echeance.minusDays(30), echeance, solde, creeLe);
        return id("crm_facture", "reference", reference);
    }

    private Long modele(String module, Long adminId) {
        jdbc.update("insert into crm_modele_message (module, nom, type_campagne, contenu, cree_par, created_at) values (?, ?, 'APRES_ECHEANCE', 'Bonjour', ?, utc_timestamp())",
                module, "IT evolution " + module, adminId);
        return id("crm_modele_message", "nom", "IT evolution " + module);
    }

    private Long campagne(String module, Long modele, Long adminId) {
        jdbc.update("""
                insert into crm_campagne (module, nom, type_campagne, modele_id, criteres, statut, cree_par, created_at)
                values (?, ?, 'APRES_ECHEANCE', ?, '{}', 'TERMINEE', ?, utc_timestamp())""", module, "IT evolution " + module, modele, adminId);
        return id("crm_campagne", "nom", "IT evolution " + module);
    }

    private Long message(Long campagne, Long clientId, Long agentId, String statut, LocalDateTime envoyeLe) {
        jdbc.update("""
                insert into crm_campagne_message (campagne_id, client_id, agent_id, statut, envoye_le, created_at)
                values (?, ?, ?, ?, ?, ?)""", campagne, clientId, agentId, statut, envoyeLe, envoyeLe);
        return jdbc.queryForObject("select max(id) from crm_campagne_message", Long.class);
    }

    private Long id(String table, String colonne, String valeur) {
        return jdbc.queryForObject("select id from " + table + " where " + colonne + " = ?", Long.class, valeur);
    }
}
