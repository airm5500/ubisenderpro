package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Aide et guidage : interrupteurs (entreprise et personnel) et textes reecrits par l'administrateur. */
class AideIT extends IntegrationBase {

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @AfterEach
    void restaurer() {
        jdbc.update("update crm_parametre set valeur = 'OUI', modifie_par = null where cle = 'aide.guidage'");
    }

    @Test
    void leGuidageEstActifParDefautEtSansTexteReecrit() throws Exception {
        mvc.perform(get("/api/aide").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guidageEntreprise").value(true))
                .andExpect(jsonPath("$.guidageUtilisateur").value(true))
                .andExpect(jsonPath("$.textes").isEmpty());
        mvc.perform(get("/api/aide")).andExpect(status().isUnauthorized());
    }

    @Test
    void chacunCoupeLeGuidagePourLuiSeulEtLAdministrateurPourTous() throws Exception {
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);

        mvc.perform(put("/api/aide/preference").session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guidageUtilisateur").value(false));
        assertThat(jdbc.queryForObject("select guidage_actif from crm_utilisateur where login = 'it-agent'", Boolean.class)).isFalse();
        mvc.perform(get("/api/aide").session(admin)).andExpect(jsonPath("$.guidageUtilisateur").value(true));

        // Le parametre de l'entreprise coupe tout le monde, chacun gardant son choix
        mvc.perform(put("/api/parametres").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"cle\":\"aide.guidage\",\"valeur\":\"NON\"}]")).andExpect(status().isOk());
        mvc.perform(get("/api/aide").session(admin))
                .andExpect(jsonPath("$.guidageEntreprise").value(false)).andExpect(jsonPath("$.guidageUtilisateur").value(true));

        mvc.perform(put("/api/aide/preference").session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.champs[0].champ").value("actif"));
    }

    @Test
    void lAdministrateurReecritPuisRetablitUnTexte() throws Exception {
        String texte = "{\"type\":\"PAGE\",\"cle\":\"factures\",\"titre\":\"Nos factures\",\"texte\":\"1. Filtrer\\n2. Relancer\"}";
        mvc.perform(put("/api/aide/textes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(texte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Nos factures"))
                .andExpect(jsonPath("$.modifiePar").isNotEmpty());
        // Une seconde ecriture remplace la premiere
        mvc.perform(put("/api/aide/textes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(texte.replace("Nos factures", "Factures à suivre"))).andExpect(jsonPath("$.titre").value("Factures à suivre"));
        assertThat(jdbc.queryForObject("select count(*) from crm_aide_texte", Integer.class)).isEqualTo(1);

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/aide").session(agent))
                .andExpect(jsonPath("$.textes[0].cle").value("factures")).andExpect(jsonPath("$.textes[0].texte").value("1. Filtrer\n2. Relancer"));
        // Seul le droit Parametres reecrit les textes
        mvc.perform(put("/api/aide/textes").session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(texte))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/aide/textes/reinitialisation").session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PAGE\",\"cle\":\"factures\"}")).andExpect(status().isForbidden());

        mvc.perform(post("/api/aide/textes/reinitialisation").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PAGE\",\"cle\":\"factures\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from crm_aide_texte", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'PARAMETRE_AIDE_MODIFICATION'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void unTexteIncompletOuDeTypeInconnuEstRefuse() throws Exception {
        mvc.perform(put("/api/aide/textes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"MENU\",\"cle\":\"factures\",\"titre\":\"\",\"texte\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champs.length()").value(2));
        mvc.perform(put("/api/aide/textes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PAGE\",\"cle\":\"factures\",\"titre\":\"t\",\"texte\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/aide/textes").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PAGE\",\"cle\":\"factures\",\"titre\":\"t\",\"texte\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
