package ci.ubipharm.crm;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests d'integration contre une vraie base MariaDB (ubicrm_test, videe a chaque lancement).
 * Lancement : ./mvnw verify -Pit
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class SecuriteEtCloisonnementIT {

    private static final String MOT_DE_PASSE_ADMIN = "Admin@2026";

    @TestConfiguration
    static class BaseVierge {
        @Bean
        FlywayMigrationStrategy nettoyerPuisMigrer() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void donnees() {
        jdbc.update("delete from crm_contact");
        jdbc.update("delete from crm_client");
        jdbc.update("delete from crm_utilisateur_agence");
        jdbc.update("delete from crm_habilitation where utilisateur_id <> (select id from crm_utilisateur where login = 'admin')");
        jdbc.update("delete from crm_journal_connexion");
        jdbc.update("delete from crm_utilisateur where login <> 'admin'");
        jdbc.update("update crm_utilisateur set statut = 'ACTIF', echecs_connexion = 0 where login = 'admin'");

        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH1', 'E2E Pharmacie Yopougon', 1, utc_timestamp())");
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH2', 'E2E Pharmacie Vridi', 2, utc_timestamp())");

        String hash = jdbc.queryForObject("select mot_de_passe_hash from crm_utilisateur where login = 'admin'", String.class);
        jdbc.update("insert into crm_utilisateur (login, nom_complet, mot_de_passe_hash, created_at) values ('e2e-agent', 'E2E Agent', ?, utc_timestamp())", hash);
        jdbc.update("""
                insert into crm_habilitation (utilisateur_id, role_id, module_id, created_at)
                select u.id, r.id, m.id, utc_timestamp() from crm_utilisateur u, crm_role r, crm_module m
                where u.login = 'e2e-agent' and r.code = 'AGENT' and m.code = 'RECOUVREMENT'
                """);
        jdbc.update("insert into crm_utilisateur_agence (utilisateur_id, agence_id, date_debut, created_at) "
                + "select id, 1, utc_date(), utc_timestamp() from crm_utilisateur where login = 'e2e-agent'");
    }

    @Test
    void unAppelAnonymeEstRefuse() throws Exception {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void laConnexionExigeLeJetonCsrf() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON).content(identifiants("admin", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAdministrateurVoitToutesLesAgences() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void lAgentNeVoitQueSonAgence() throws Exception {
        MockHttpSession session = connecter("e2e-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements", hasSize(1)))
                .andExpect(jsonPath("$.elements[*].code", contains("E2E-PH1")));

        Long clientVridi = jdbc.queryForObject("select id from crm_client where code = 'E2E-PH2'", Long.class);
        mvc.perform(get("/api/clients/" + clientVridi).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/agences").session(session)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void leCompteEstVerrouilleApresCinqEchecs() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(identifiants("e2e-agent", "mauvais")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect."));
        }
        mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(identifiants("e2e-agent", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isUnauthorized());
        String statut = jdbc.queryForObject("select statut from crm_utilisateur where login = 'e2e-agent'", String.class);
        org.assertj.core.api.Assertions.assertThat(statut).isEqualTo("DESACTIVE");
    }

    @Test
    void uneTailleDePageTropGrandeEstRefuseeAvecLeChampEnCause() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").param("taille", "500").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champs[0].champ").value("taille"));
    }

    private MockHttpSession connecter(String login, String motDePasse) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/connexion").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(identifiants(login, motDePasse)))
                .andExpect(status().isOk());
        return session;
    }

    private String identifiants(String login, String motDePasse) {
        return "{\"login\":\"" + login + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }
}
