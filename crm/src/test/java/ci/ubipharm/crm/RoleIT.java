package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

import com.jayway.jsonpath.JsonPath;

/** Roles et droits : les ecrans coches sur un role ouvrent les routes du serveur, et rien d'autre. */
class RoleIT extends IntegrationBase {

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void unRoleSansFournisseursSmsNOuvrePasCetEcran() throws Exception {
        Long id = creerRole("Administrateur sans SMS", "[\"UTILISATEURS\", \"PARAMETRES\"]");
        assertThat(jdbc.queryForObject("select code from crm_role where id = ?", String.class, id)).isEqualTo("ADMINISTRATEUR_SANS_SMS");
        creerUtilisateur("it-admin-partiel", "ADMINISTRATEUR_SANS_SMS", "RECOUVREMENT", 1L);
        MockHttpSession partiel = connecter("it-admin-partiel", MOT_DE_PASSE_ADMIN);

        mvc.perform(get("/api/auth/moi").session(partiel))
                .andExpect(jsonPath("$.droits").value(hasItem("PARAMETRES")))
                .andExpect(jsonPath("$.droits").value(not(hasItem("FOURNISSEURS_SMS"))))
                .andExpect(jsonPath("$.profil").value("Administrateur sans SMS"));
        mvc.perform(get("/api/parametres").session(partiel)).andExpect(status().isOk());
        mvc.perform(get("/api/utilisateurs").session(partiel)).andExpect(status().isOk());
        mvc.perform(get("/api/sms/fournisseurs").session(partiel)).andExpect(status().isForbidden());
        mvc.perform(get("/api/roles").session(partiel)).andExpect(status().isForbidden());
        mvc.perform(get("/api/imports").session(partiel)).andExpect(status().isForbidden());
    }

    @Test
    void retirerUnDroitFermeLaSessionDesTitulaires() throws Exception {
        Long id = creerRole("Paramétreur", "[\"PARAMETRES\"]");
        creerUtilisateur("it-parametreur", "PARAMETREUR", "RECOUVREMENT", 1L);
        MockHttpSession parametreur = connecter("it-parametreur", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/parametres").session(parametreur)).andExpect(status().isOk());

        mvc.perform(put("/api/roles/" + id).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"Paramétreur\", \"droits\": [\"FOURNISSEURS_SMS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.droits[0]").value("FOURNISSEURS_SMS"));
        mvc.perform(get("/api/parametres").session(parametreur)).andExpect(status().isUnauthorized());

        parametreur = connecter("it-parametreur", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/parametres").session(parametreur)).andExpect(status().isForbidden());
        mvc.perform(get("/api/sms/fournisseurs").session(parametreur)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select details from crm_journal_action where action = 'ROLE_MODIFICATION'", String.class))
                .contains("ajoutés : FOURNISSEURS_SMS").contains("retirés : PARAMETRES");
        // La case decochee reste en base, desactivee
        assertThat(jdbc.queryForObject("select count(*) from crm_role_droit where role_id = ? and actif = false", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void lAdministrateurGardeLaMainSurLesComptesEtLesRoles() throws Exception {
        Long adminId = jdbc.queryForObject("select id from crm_role where code = 'ADMIN'", Long.class);
        mvc.perform(put("/api/roles/" + adminId).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"Administrateur\", \"visionGlobale\": true, \"droits\": [\"PARAMETRES\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le rôle Administrateur garde toujours « Utilisateurs et habilitations » et « Rôles et droits »."));
        mvc.perform(post("/api/roles/" + adminId + "/desactivation").session(admin).with(csrf())).andExpect(status().isBadRequest());

        Long id = creerRole("Superviseur", "[\"FACTURES\"]");
        creerUtilisateur("it-superviseur", "SUPERVISEUR", "RECOUVREMENT", 1L);
        mvc.perform(post("/api/roles/" + id + "/desactivation").session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce rôle est attribué à 1 compte actif : retirez-le-leur d'abord."));
        mvc.perform(post("/api/roles").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"superviseur\", \"droits\": [\"FACTURES\"]}"))
                .andExpect(status().isBadRequest());

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/roles").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void unDroitDuRecouvrementNeVientPasDUnRoleEnMarketing() throws Exception {
        creerUtilisateur("it-dir-marketing", "DIR_AGENCE", "MARKETING", 1L);
        MockHttpSession directeur = connecter("it-dir-marketing", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/auth/moi").session(directeur))
                .andExpect(jsonPath("$.droits").value(hasItem("IMPORTS")))
                .andExpect(jsonPath("$.droits").value(not(hasItem("FACTURES"))));
        mvc.perform(get("/api/recouvrement/agents").session(directeur)).andExpect(status().isForbidden());
    }

    private Long creerRole(String libelle, String droits) throws Exception {
        String reponse = mvc.perform(post("/api/roles").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"" + libelle + "\", \"droits\": " + droits + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(reponse, "$.id")).longValue();
    }
}
