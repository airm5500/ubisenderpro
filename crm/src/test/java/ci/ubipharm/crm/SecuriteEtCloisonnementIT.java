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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Authentification, CSRF, verrouillage et cloisonnement par agence. */
class SecuriteEtCloisonnementIT extends IntegrationBase {

    @BeforeEach
    void donnees() {
        reinitialiser();
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH1', 'E2E Pharmacie Yopougon', 1, utc_timestamp())");
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH2', 'E2E Pharmacie Vridi', 2, utc_timestamp())");

        creerUtilisateur("e2e-agent", "AGENT", "RECOUVREMENT", 1L);
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
}
