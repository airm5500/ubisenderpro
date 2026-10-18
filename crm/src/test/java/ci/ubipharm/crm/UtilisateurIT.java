package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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

/** Comptes crees par l'administrateur : roles par module, agences, mot de passe provisoire, desactivation. */
class UtilisateurIT extends IntegrationBase {

    private static final String DEMANDE = """
            {"login":"Awa.Kone","nomComplet":"Awa Koné","email":"awa@ubipharm.ci","numeroWhatsapp":"07 08 12 34 56",
             "habilitations":[{"roleCode":"AGENT","moduleCode":"RECOUVREMENT"},{"roleCode":"AGENT","moduleCode":"RECOUVREMENT"}],
             "agenceIds":[1]}
            """;

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void lAdministrateurCreeUnCompteQuiDoitChangerSonMotDePasse() throws Exception {
        String motDePasse = creer(DEMANDE);
        assertThat(motDePasse).hasSize(12).containsPattern("[a-zA-Z]").containsPattern("[0-9]");

        mvc.perform(get("/api/utilisateurs").param("q", "awa").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].login").value("awa.kone"))
                .andExpect(jsonPath("$.elements[0].numeroWhatsapp").value("2250708123456"))
                .andExpect(jsonPath("$.elements[0].habilitations", hasSize(1)))
                .andExpect(jsonPath("$.elements[0].agences[0].agenceId").value(1));

        MockHttpSession agent = connecter("awa.kone", motDePasse);
        mvc.perform(get("/api/auth/moi").session(agent)).andExpect(jsonPath("$.motDePasseProvisoire").value(true));
        mvc.perform(get("/api/clients").session(agent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Remplacez votre mot de passe provisoire pour continuer."));

        changer(agent, motDePasse, "court1").andExpect(status().isBadRequest());
        changer(agent, motDePasse, "awa.kone2026xx").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le nouveau mot de passe ne doit pas contenir votre identifiant."));
        changer(agent, "faux", "Recouvrement2026").andExpect(status().isBadRequest());
        changer(agent, motDePasse, "Recouvrement2026").andExpect(status().isNoContent());

        mvc.perform(get("/api/clients").session(agent)).andExpect(status().isOk());
        connecter("awa.kone", "Recouvrement2026");
    }

    @Test
    void unLoginOuUnNumeroDejaPrisEstRefuse() throws Exception {
        creer(DEMANDE);
        mvc.perform(post("/api/utilisateurs").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DEMANDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'identifiant « awa.kone » est déjà utilisé."));
        mvc.perform(post("/api/utilisateurs").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(DEMANDE.replace("Awa.Kone", "awa2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce numéro est déjà celui de Awa Koné."));
    }

    @Test
    void modifierLesDroitsFermeLaSessionOuverteEtGardeLHistorique() throws Exception {
        String motDePasse = creer(DEMANDE);
        Long id = jdbc.queryForObject("select id from crm_utilisateur where login = 'awa.kone'", Long.class);
        jdbc.update("update crm_utilisateur set mot_de_passe_provisoire = false where id = ?", id);
        MockHttpSession agent = connecter("awa.kone", motDePasse);
        mvc.perform(get("/api/clients").session(agent)).andExpect(status().isOk());

        // Nom seul : la session reste ouverte
        modifier(id, DEMANDE.replace("Awa Koné", "Awa Koné Traoré")).andExpect(status().isOk());
        mvc.perform(get("/api/clients").session(agent)).andExpect(status().isOk());

        // Role marketing sur tous les modules et agence 2 a la place de 1 : reconnexion obligatoire
        modifier(id, """
                {"nomComplet":"Awa Koné","habilitations":[{"roleCode":"DIR_AGENCE","moduleCode":""}],"agenceIds":[2]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitations", hasSize(1)))
                .andExpect(jsonPath("$.habilitations[0].moduleCode").doesNotExist())
                .andExpect(jsonPath("$.agences[0].agenceId").value(2))
                .andExpect(jsonPath("$.numeroWhatsapp").doesNotExist());
        mvc.perform(get("/api/clients").session(agent)).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from crm_habilitation where utilisateur_id = ? and actif = false", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from crm_utilisateur_agence where utilisateur_id = ? and date_fin is not null", Integer.class, id))
                .isEqualTo(1);

        // Retour au role d'origine : la ligne desactivee est reprise, pas dupliquee
        modifier(id, DEMANDE).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from crm_habilitation where utilisateur_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void desactiverFermeLaSessionEtReinitialiserDebloque() throws Exception {
        String motDePasse = creer(DEMANDE);
        Long id = jdbc.queryForObject("select id from crm_utilisateur where login = 'awa.kone'", Long.class);
        jdbc.update("update crm_utilisateur set mot_de_passe_provisoire = false where id = ?", id);
        MockHttpSession agent = connecter("awa.kone", motDePasse);

        mvc.perform(post("/api/utilisateurs/" + id + "/desactivation").session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DESACTIVE"));
        mvc.perform(get("/api/auth/moi").session(agent)).andExpect(status().isUnauthorized());

        String nouveau = JsonPath.read(mvc.perform(post("/api/utilisateurs/" + id + "/reinitialisation-mot-de-passe").session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.statut").value("ACTIF"))
                .andExpect(jsonPath("$.utilisateur.motDePasseProvisoire").value(true))
                .andReturn().getResponse().getContentAsString(), "$.motDePasseProvisoire");
        connecter("awa.kone", nouveau);
    }

    @Test
    void ilResteToujoursUnAdministrateur() throws Exception {
        Long adminId = jdbc.queryForObject("select id from crm_utilisateur where login = 'admin'", Long.class);
        mvc.perform(post("/api/utilisateurs/" + adminId + "/desactivation").session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vous ne pouvez pas désactiver votre propre compte."));
        modifier(adminId, """
                {"nomComplet":"Administrateur","habilitations":[{"roleCode":"DG"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vous ne pouvez pas retirer votre propre rôle d'administrateur."));
    }

    @Test
    void seulLAdministrateurGereLesComptes() throws Exception {
        creerUtilisateur("e2e-dr", "DIR_RECOUVREMENT", "RECOUVREMENT", null);
        MockHttpSession dr = connecter("e2e-dr", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/utilisateurs").session(dr)).andExpect(status().isForbidden());
        mvc.perform(get("/api/utilisateurs/referentiel").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", hasSize(2)));
    }

    private String creer(String demande) throws Exception {
        String reponse = mvc.perform(post("/api/utilisateurs").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(demande))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.motDePasseProvisoire");
    }

    private org.springframework.test.web.servlet.ResultActions modifier(Long id, String demande) throws Exception {
        return mvc.perform(put("/api/utilisateurs/" + id).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(demande));
    }

    private org.springframework.test.web.servlet.ResultActions changer(MockHttpSession session, String actuel, String nouveau) throws Exception {
        return mvc.perform(post("/api/auth/mot-de-passe").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actuel\":\"" + actuel + "\",\"nouveau\":\"" + nouveau + "\"}"));
    }
}
