package ci.ubipharm.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Referentiel des agences : la Direction Generale en cree, renomme et archive ; une agence occupee ne s'archive pas. */
class AgenceIT extends IntegrationBase {

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void laDirectionGereLesAgencesSansJamaisLesSupprimer() throws Exception {
        mvc.perform(get("/api/agences/gestion").session(admin)).andExpect(jsonPath("$.length()").value(5));
        Long sanPedro = idDe(envoyer(admin, post("/api/agences"), "{\"code\":\"spe\",\"codeImport\":\"s\",\"libelle\":\"San Pedro\"}")
                .andExpect(jsonPath("$.code").value("SPE")).andExpect(jsonPath("$.codeImport").value("S")));
        envoyer(admin, post("/api/agences"), "{\"code\":\"YOP\",\"libelle\":\"Autre\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le code YOP est déjà celui d'une autre agence."));
        envoyer(admin, put("/api/agences/" + sanPedro), "{\"code\":\"SPE\",\"codeImport\":\"Y\",\"libelle\":\"San-Pédro\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le code d'import Y désigne déjà une autre agence."));
        envoyer(admin, put("/api/agences/" + sanPedro), "{\"code\":\"SPE\",\"codeImport\":\"S\",\"libelle\":\"San-Pédro\"}")
                .andExpect(jsonPath("$.libelle").value("San-Pédro"));
        mvc.perform(get("/api/agences").session(admin)).andExpect(jsonPath("$[*].libelle", Matchers.hasItem("San-Pédro")));

        // Une agence qui a des clients ne s'archive pas
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('A-C1', 'Pharmacie A', 1, utc_timestamp())");
        envoyer(admin, post("/api/agences/1/archivage"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'agence Yopougon a encore 1 client(s) actif(s) et 0 utilisateur(s) : "
                        + "rattachez-les d'abord à une autre agence."));
        envoyer(admin, post("/api/agences/" + sanPedro + "/archivage"), "").andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(get("/api/agences").session(admin)).andExpect(jsonPath("$[*].libelle", Matchers.not(Matchers.hasItem("San-Pédro"))));
        envoyer(admin, post("/api/agences/" + sanPedro + "/reactivation"), "").andExpect(jsonPath("$.statut").value("ACTIF"));

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        envoyer(agent, post("/api/agences"), "{\"code\":\"ABJ\",\"libelle\":\"Abidjan\"}").andExpect(status().isForbidden());
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
