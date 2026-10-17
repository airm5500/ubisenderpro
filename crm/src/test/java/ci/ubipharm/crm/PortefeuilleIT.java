package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Portefeuilles : affectation, reaffectation avec historique, transfert, interim et cloisonnement. */
class PortefeuilleIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final long VRIDI = 2L;

    private MockHttpSession admin;
    private Long agent1;
    private Long agent2;
    private Long agentVridi;
    private Long c1;
    private Long c2;
    private Long cVridi;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent2", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent-vridi", "AGENT", "RECOUVREMENT", VRIDI);
        agent1 = id("crm_utilisateur", "login", "it-agent1");
        agent2 = id("crm_utilisateur", "login", "it-agent2");
        agentVridi = id("crm_utilisateur", "login", "it-agent-vridi");
        c1 = client("P-C1", YOPOUGON);
        c2 = client("P-C2", YOPOUGON);
        cVridi = client("P-C3", VRIDI);
        facture("P-F1", c2, "150000.00");
        facture("P-F2", c2, "50000.00");
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void uneReaffectationCloLAffectationPrecedenteEtGardeLHistorique() throws Exception {
        affecter(admin, agent1, "Demarrage du pilote", c1, c2, cVridi, 999999L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbTraites").value(2))
                .andExpect(jsonPath("$.refus", hasSize(2)))
                .andExpect(jsonPath("$.refus[0]", containsString("P-C3 : client de l'agence Vridi, hors des agences de Test it-agent1")))
                .andExpect(jsonPath("$.refus[1]", containsString("introuvable")));

        affecter(admin, agent2, null, c1).andExpect(jsonPath("$.nbTraites").value(1));
        affecter(admin, agent1, null, c2).andExpect(jsonPath("$.nbInchanges").value(1));

        mvc.perform(get("/api/recouvrement/clients/" + c1 + "/affectations").session(admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].agentNom").value("Test it-agent2"))
                .andExpect(jsonPath("$[0].dateFin").doesNotExist())
                .andExpect(jsonPath("$[1].agentNom").value("Test it-agent1"))
                .andExpect(jsonPath("$[1].motifDebut").value("Demarrage du pilote"))
                .andExpect(jsonPath("$[1].motifFin").value("Reaffecte a Test it-agent2"))
                .andExpect(jsonPath("$[1].retirePar").value("Administrateur"));

        // La base refuse deux affectations en cours pour un meme client
        assertThatThrownBy(() -> jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) "
                + "values (?, ?, utc_timestamp(), ?)", c1, agent1, agent1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lAgentNeVoitQueSonPortefeuilleEtSesFactures() throws Exception {
        affecter(admin, agent1, null, c2);
        affecter(admin, agent2, null, c1);

        MockHttpSession agent = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/recouvrement/portefeuille").session(agent))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].code").value("P-C2"))
                .andExpect(jsonPath("$.elements[0].nbFacturesOuvertes").value(2))
                .andExpect(jsonPath("$.elements[0].totalSolde").value(200000.00))
                .andExpect(jsonPath("$.elements[0].retardMax").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/recouvrement/factures").session(agent)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/recouvrement/factures").session(connecter("it-agent2", MOT_DE_PASSE_ADMIN)))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/recouvrement/agents").session(agent)).andExpect(status().isForbidden());
        affecter(agent, agent1, null, c1).andExpect(status().isForbidden());

        mvc.perform(get("/api/recouvrement/portefeuille").param("sansAgent", "true").session(admin))
                .andExpect(jsonPath("$.elements[*].code", contains("P-C3")));
        mvc.perform(get("/api/recouvrement/agents").param("agenceId", String.valueOf(YOPOUGON)).session(admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nbClients").value(1))
                .andExpect(jsonPath("$[0].totalSolde").value(200000.00))
                .andExpect(jsonPath("$[0].agences").value("Yopougon"));
    }

    @Test
    void leTransfertDePortefeuilleSuitLeDepartDUnAgent() throws Exception {
        affecter(admin, agent1, null, c1, c2);
        mvc.perform(post("/api/recouvrement/agents/" + agent1 + "/transfert").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versAgentId\":" + agent2 + "}"))
                .andExpect(jsonPath("$.nbTraites").value(2));
        assertThat(jdbc.queryForObject("select count(*) from crm_affectation where agent_id = ? and date_fin is null", Long.class, agent2))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select motif_debut from crm_affectation where client_id = ? and date_fin is null", String.class, c1))
                .isEqualTo("Transfert du portefeuille de Test it-agent1");
        mvc.perform(post("/api/recouvrement/agents/" + agent1 + "/transfert").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versAgentId\":" + agent2 + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le portefeuille de Test it-agent1 est vide."));
    }

    @Test
    void leRemplacantGereLePortefeuilleDeLAbsentPendantLaDelegation() throws Exception {
        affecter(admin, agent1, null, c2);
        affecter(admin, agent2, null, c1);
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);

        String delegation = deleguer(admin, agent1, agent2, aujourdhui, aujourdhui.plusDays(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCours").value(true))
                .andExpect(jsonPath("$.autorisePar").value("Administrateur"))
                .andReturn().getResponse().getContentAsString();
        Long delegationId = ((Number) JsonPath.read(delegation, "$.id")).longValue();

        MockHttpSession remplacant = connecter("it-agent2", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/recouvrement/portefeuille").session(remplacant)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/recouvrement/factures").session(remplacant)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/recouvrement/delegations").param("enCours", "true").session(remplacant))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/recouvrement/agents").session(admin))
                .andExpect(jsonPath("$[?(@.login == 'it-agent1')].remplacant", contains("Test it-agent2")));

        // Pas de chevauchement, et seule la direction autorise un interim
        deleguer(admin, agent1, agentVridi, aujourdhui.plusDays(3), aujourdhui.plusDays(8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("est deja remplace par Test it-agent2")));
        deleguer(remplacant, agent2, agent1, aujourdhui, aujourdhui).andExpect(status().isForbidden());

        mvc.perform(post("/api/recouvrement/delegations/" + delegationId + "/annulation").session(admin).with(csrf()))
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.enCours").value(false));
        mvc.perform(get("/api/recouvrement/portefeuille").session(remplacant)).andExpect(jsonPath("$.total").value(1));

        // Une delegation qui commence demain ne donne encore aucun acces
        deleguer(admin, agent1, agent2, aujourdhui.plusDays(1), aujourdhui.plusDays(2)).andExpect(status().isOk());
        mvc.perform(get("/api/recouvrement/portefeuille").session(remplacant)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void unDirecteurDAgenceResteDansSesAgences() throws Exception {
        creerUtilisateur("it-dir", "DIR_AGENCE", "RECOUVREMENT", YOPOUGON);
        MockHttpSession directeur = connecter("it-dir", MOT_DE_PASSE_ADMIN);
        affecter(directeur, agent1, null, c1).andExpect(jsonPath("$.nbTraites").value(1));
        affecter(directeur, agentVridi, null, cVridi).andExpect(status().isNotFound());
        mvc.perform(get("/api/recouvrement/portefeuille").session(directeur))
                .andExpect(jsonPath("$.elements[*].code", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("P-C3"))));
        mvc.perform(get("/api/recouvrement/clients/" + cVridi + "/affectations").session(directeur)).andExpect(status().isNotFound());
        deleguer(directeur, agent1, agent2, LocalDate.now(ZoneOffset.UTC), LocalDate.now(ZoneOffset.UTC)).andExpect(status().isForbidden());
    }

    @Test
    void leNumeroWhatsappDUnAgentEstNormaliseEtUnique() throws Exception {
        mvc.perform(put("/api/recouvrement/agents/" + agent1 + "/numero").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroWhatsapp\":\"07 11 22 33 44\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value("2250711223344"));
        mvc.perform(put("/api/recouvrement/agents/" + agent2 + "/numero").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroWhatsapp\":\"+225 0711223344\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce numero est deja celui de Test it-agent1."));
        mvc.perform(put("/api/recouvrement/agents/" + agent2 + "/numero").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroWhatsapp\":\"1234\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recouvrement/delegations").session(admin)).andExpect(jsonPath("$", empty()));
    }

    private ResultActions affecter(MockHttpSession session, Long agentId, String motif, Long... clientIds) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(clientIds).map(String::valueOf).toList());
        String corps = "{\"agentId\":" + agentId + ",\"clientIds\":[" + ids + "]" + (motif == null ? "" : ",\"motif\":\"" + motif + "\"") + "}";
        return mvc.perform(post("/api/recouvrement/affectations").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private ResultActions deleguer(MockHttpSession session, Long titulaire, Long remplacant, LocalDate debut, LocalDate fin) throws Exception {
        return mvc.perform(post("/api/recouvrement/delegations").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulaireId\":" + titulaire + ",\"remplacantId\":" + remplacant + ",\"dateDebut\":\"" + debut
                        + "\",\"dateFin\":\"" + fin + "\",\"motif\":\"Conges\"}"));
    }

    private Long client(String code, long agenceId) {
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values (?, ?, ?, utc_timestamp())",
                code, "Pharmacie " + code, agenceId);
        return id("crm_client", "code", code);
    }

    private void facture(String reference, Long clientId, String solde) {
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), '2026-01-01', '2026-01-31', ?, ?, utc_timestamp())
                """, reference, clientId, solde, solde);
    }

    private Long id(String table, String colonne, String valeur) {
        return jdbc.queryForObject("select id from " + table + " where " + colonne + " = ?", Long.class, valeur);
    }
}
