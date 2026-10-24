package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Ecran Parametres : valeurs controlees selon leur type, toutes ou aucune, journalisees ; reserve a l'administrateur. */
class ParametreIT extends IntegrationBase {

    private MockHttpSession admin;
    private List<Map<String, Object>> sauvegarde;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        sauvegarde = jdbc.queryForList("select cle, valeur from crm_parametre");
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @AfterEach
    void restaurer() {
        sauvegarde.forEach(p -> jdbc.update("update crm_parametre set valeur = ?, modifie_par = null where cle = ?", p.get("valeur"), p.get("cle")));
    }

    @Test
    void lesParametresSeModifientEnsembleEtSontJournalises() throws Exception {
        mvc.perform(get("/api/parametres").session(admin))
                .andExpect(jsonPath("$[?(@.cle == 'admin.mot_de_passe_initial')]").isEmpty())
                .andExpect(jsonPath("$[?(@.cle == 'sms.envoi.heure_debut')].typeValeur").value("HEURE"));

        modifier("[{\"cle\":\"sms.envoi.heure_debut\",\"valeur\":\" 7 \"},{\"cle\":\"application.url_publique\",\"valeur\":\"https://crm.ubipharm.ci/\"},"
                + "{\"cle\":\"email.expediteur\",\"valeur\":\"Relances@UBIPHARM.ci\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cle == 'sms.envoi.heure_debut')].modifiePar").value(nomAdmin()));
        assertThat(valeur("sms.envoi.heure_debut")).isEqualTo("7");
        assertThat(valeur("application.url_publique")).isEqualTo("https://crm.ubipharm.ci");
        assertThat(valeur("email.expediteur")).isEqualTo("relances@ubipharm.ci");
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'PARAMETRE_MODIFICATION'", Integer.class)).isEqualTo(3);

        // Une valeur fausse annule tout l'enregistrement
        modifier("[{\"cle\":\"sms.envoi.plafond_jour\",\"valeur\":\"500\"},{\"cle\":\"sms.envoi.heure_fin\",\"valeur\":\"6\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS : l'heure de fin d'envoi doit suivre l'heure de début."));
        assertThat(valeur("sms.envoi.plafond_jour")).isNotEqualTo("500");
    }

    @Test
    void chaqueTypeEstControle() throws Exception {
        modifier("[{\"cle\":\"whatsapp.envoi.plafond_jour\",\"valeur\":\"beaucoup\"}]")
                .andExpect(jsonPath("$.message").value("« Messages par numéro et par jour, tous envois confondus » : saisissez un nombre entier entre 1 et 1000."));
        // Regles anti-blocage : bornes et ordre des durees de la longue pause
        modifier("[{\"cle\":\"whatsapp.envoi.plafond_heure\",\"valeur\":\"0\"}]")
                .andExpect(jsonPath("$.message").value("« Messages par numéro et par heure, tous envois confondus » : saisissez un nombre entier entre 1 et 500."));
        modifier("[{\"cle\":\"whatsapp.envoi.pause_min_minutes\",\"valeur\":\"20\"},{\"cle\":\"whatsapp.envoi.pause_max_minutes\",\"valeur\":\"10\"}]")
                .andExpect(jsonPath("$.message").value("WhatsApp : la durée minimale de la longue pause dépasse sa durée maximale."));
        assertThat(valeur("whatsapp.envoi.pause_min_minutes")).isEqualTo("5");
        modifier("[{\"cle\":\"application.url_publique\",\"valeur\":\"http://crm\"}]")
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith(": l'adresse commence par https://.")));
        modifier("[{\"cle\":\"email.expediteur\",\"valeur\":\"relances\"}]").andExpect(status().isBadRequest());
        modifier("[{\"cle\":\"sms.envoi.plafond_jour\",\"valeur\":\" \"}]")
                .andExpect(jsonPath("$.message").value("« SMS de relance par jour, toutes campagnes confondues » ne peut pas rester vide."));
        modifier("[{\"cle\":\"admin.mot_de_passe_initial\",\"valeur\":\"x\"}]")
                .andExpect(jsonPath("$.message").value("Paramètre inconnu ou en double : admin.mot_de_passe_initial."));
        modifier("[{\"cle\":\"application.url_publique\",\"valeur\":\"\"}]").andExpect(status().isOk());

        creerUtilisateur("it-dr", "DIR_RECOUVREMENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/parametres").session(connecter("it-dr", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    private ResultActions modifier(String json) throws Exception {
        return mvc.perform(put("/api/parametres").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String nomAdmin() {
        return jdbc.queryForObject("select nom_complet from crm_utilisateur where login = 'admin'", String.class);
    }

    private String valeur(String cle) {
        return jdbc.queryForObject("select valeur from crm_parametre where cle = ?", String.class, cle);
    }
}
