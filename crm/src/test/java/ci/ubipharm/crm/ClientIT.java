package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Clients arrives un par un : saisis a la main, modifies depuis la fiche, sans jamais perdre un ancien numero. */
class ClientIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final long AUTRE_AGENCE = 2L;

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void unNouveauClientEstSaisiAvecSesNumeros() throws Exception {
        String reponse = enregistrer(admin, post("/api/clients"), """
                {"code":"c-9001","raisonSociale":"Pharmacie du Rond-Point","agenceId":1,"segmentation":"P","nomPharmacien":"Dr Kouassi",
                 "email":"Rond.Point@exemple.ci","numeros":{"PRINCIPAL":"07 00 00 00 01","TITULAIRE":"05 00 00 00 02"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("c-9001"))
                .andExpect(jsonPath("$.numeroPrincipal").value("2250700000001"))
                .andExpect(jsonPath("$.numeros.TITULAIRE").value("2250500000002"))
                .andExpect(jsonPath("$.email").value("rond.point@exemple.ci"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(reponse, "$.id")).longValue();
        assertThat(jdbc.queryForObject("select nom from crm_contact where client_id = ? and fonction = 'TITULAIRE'", String.class, id))
                .isEqualTo("Dr Kouassi");
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'CLIENT_CREATION' and entite_id = ?", Integer.class, id))
                .isEqualTo(1);

        enregistrer(admin, post("/api/clients"), """
                {"code":"C-9001","raisonSociale":"Doublon","agenceId":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le numéro de compte C-9001 existe déjà."));
        enregistrer(admin, post("/api/clients"), """
                {"code":"C-9002","raisonSociale":"Pharmacie Erreurs","agenceId":1,"segmentation":"Z","email":"faux","numeros":{"PRINCIPAL":"123"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Segment inconnu : Z."),
                        org.hamcrest.Matchers.containsString("Adresse e-mail invalide : faux."),
                        org.hamcrest.Matchers.containsString("invalide : 123"))));
    }

    @Test
    void unNumeroRemplaceEstArchiveEtLaModificationJournalisee() throws Exception {
        String reponse = enregistrer(admin, post("/api/clients"), """
                {"code":"C-9010","raisonSociale":"Pharmacie Avant","agenceId":1,"ville":"Abidjan",
                 "numeros":{"PRINCIPAL":"0700000001","COMPTABLE":"0700000003"}}
                """).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(reponse, "$.id")).longValue();

        enregistrer(admin, put("/api/clients/" + id), """
                {"code":"IGNORE","raisonSociale":"Pharmacie Après","agenceId":1,"numeros":{"PRINCIPAL":"0700000009"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C-9010"))
                .andExpect(jsonPath("$.raisonSociale").value("Pharmacie Après"))
                .andExpect(jsonPath("$.ville").doesNotExist())
                .andExpect(jsonPath("$.numeroPrincipal").value("2250700000009"))
                .andExpect(jsonPath("$.numeros.COMPTABLE").doesNotExist());
        // Rien n'est supprime : les deux anciens numeros restent, archives
        assertThat(jdbc.queryForObject("select count(*) from crm_contact where client_id = ? and statut = 'ARCHIVE'", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select details from crm_journal_action where action = 'CLIENT_MODIFICATION'", String.class))
                .contains("Pharmacie : Pharmacie Avant -> Pharmacie Après")
                .contains("Ville : Abidjan -> vide")
                .contains("2250700000003 -> vide");
    }

    @Test
    void seulUnProfilAutoriseCreeDesClientsDansSonPerimetre() throws Exception {
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", YOPOUGON);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/auth/moi").session(agent))
                .andExpect(jsonPath("$.droits").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CLIENTS_GERER"))));
        enregistrer(agent, post("/api/clients"), """
                {"code":"C-9020","raisonSociale":"Pharmacie Agent","agenceId":1}
                """).andExpect(status().isForbidden());

        creerUtilisateur("it-dir", "DIR_AGENCE", "RECOUVREMENT", YOPOUGON);
        MockHttpSession directeur = connecter("it-dir", MOT_DE_PASSE_ADMIN);
        enregistrer(directeur, post("/api/clients"), """
                {"code":"C-9021","raisonSociale":"Pharmacie Ailleurs","agenceId":%d}
                """.formatted(AUTRE_AGENCE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cette agence est hors de votre périmètre."));
        enregistrer(directeur, post("/api/clients"), """
                {"code":"C-9022","raisonSociale":"Pharmacie Ici","agenceId":%d}
                """.formatted(YOPOUGON)).andExpect(status().isOk());
        mvc.perform(get("/api/clients/segments").session(directeur)).andExpect(jsonPath("$[0].code").value("P"));
    }

    private ResultActions enregistrer(MockHttpSession session,
                                      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
