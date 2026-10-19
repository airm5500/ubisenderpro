package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SegmentService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;

/** Centre de support : tickets, diagnostic des erreurs, sante et FAQ. */
class SupportIT extends IntegrationBase {

    @MockitoBean
    PasserelleWhatsappService passerelle;

    /** Sert a provoquer une erreur inattendue (reponse 500) sur un ecran existant. */
    @MockitoBean
    SegmentService segmentService;

    @Autowired
    SupportDiagnosticService supportDiagnosticService;

    private MockHttpSession admin;
    private MockHttpSession agent;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void unTicketSuitSonCycleDeVieSansJamaisEtreSupprime() throws Exception {
        // L'agent ouvre un ticket et le retrouve dans les siens
        String ticket = envoyer(agent, post("/api/support/tickets"),
                "{\"sujet\":\"Import bloqué\",\"categorie\":\"incident\",\"priorite\":\"HAUTE\",\"message\":\"L'import s'arrête à la ligne 12.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OUVERT"))
                .andExpect(jsonPath("$.categorie").value("INCIDENT"))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(ticket, "$.id")).longValue();
        mvc.perform(get("/api/support/tickets").session(agent)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sujet").value("Import bloqué"));
        envoyer(agent, post("/api/support/tickets"), "{\"sujet\":\"X\",\"categorie\":\"BUG\",\"message\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("Catégorie inconnue")));

        // Sans le droit Support : ni la liste de tous les tickets, ni le traitement, ni le diagnostic
        mvc.perform(get("/api/support/tickets/tous").session(agent)).andExpect(status().isForbidden());
        envoyer(agent, post("/api/support/tickets/" + id + "/statut"), "{\"statut\":\"RESOLU\"}").andExpect(status().isForbidden());
        envoyer(agent, post("/api/support/tickets/" + id + "/affectation"), "{\"utilisateurId\":1}").andExpect(status().isForbidden());
        mvc.perform(get("/api/support/erreurs").session(agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/sante").session(agent)).andExpect(status().isForbidden());

        // Le support le voit, le prend et repond : le ticket passe en cours
        mvc.perform(get("/api/support/tickets/tous").param("statut", "OUVERT").session(admin)).andExpect(jsonPath("$", hasSize(1)));
        Long adminId = jdbc.queryForObject("select id from crm_utilisateur where login = 'admin'", Long.class);
        Long agentId = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent'", Long.class);
        mvc.perform(get("/api/support/intervenants").session(admin)).andExpect(jsonPath("$[0].login").value("admin"));
        envoyer(admin, post("/api/support/tickets/" + id + "/affectation"), "{\"utilisateurId\":" + agentId + "}")
                .andExpect(status().isBadRequest());
        envoyer(admin, post("/api/support/tickets/" + id + "/affectation"), "{\"utilisateurId\":" + adminId + "}")
                .andExpect(jsonPath("$.assigneId").value(adminId));
        envoyer(admin, post("/api/support/tickets/" + id + "/messages"), "{\"contenu\":\"Pouvez-vous joindre le fichier ?\"}")
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.messages[2].support").value(true));

        // Resolu, relance par l'auteur : rouvert ; ferme : plus de message
        envoyer(admin, post("/api/support/tickets/" + id + "/statut"), "{\"statut\":\"RESOLU\"}").andExpect(jsonPath("$.statut").value("RESOLU"));
        envoyer(agent, post("/api/support/tickets/" + id + "/messages"), "{\"contenu\":\"Toujours bloqué.\"}")
                .andExpect(jsonPath("$.statut").value("OUVERT"));
        envoyer(admin, post("/api/support/tickets/" + id + "/statut"), "{\"statut\":\"FERME\"}").andExpect(jsonPath("$.statut").value("FERME"));
        envoyer(agent, post("/api/support/tickets/" + id + "/messages"), "{\"contenu\":\"Encore ?\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("fermé")));

        // Un autre utilisateur sans droit Support ne lit pas le ticket
        creerUtilisateur("it-autre", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession autre = connecter("it-autre", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/support/tickets/" + id).session(autre)).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/tickets/" + id).session(agent)).andExpect(jsonPath("$.messages[0].contenu").value("L'import s'arrête à la ligne 12."));

        assertThat(jdbc.queryForObject("select count(*) from crm_support_ticket where id = ? and ferme_le is not null", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForList("select action from crm_journal_action where entite = 'crm_support_ticket' order by id", String.class))
                .contains("SUPPORT_TICKET_CREATION", "SUPPORT_TICKET_AFFECTATION", "SUPPORT_TICKET_MESSAGE", "SUPPORT_TICKET_STATUT");
    }

    @Test
    void lesErreursSontDedoublonneesParSignature() throws Exception {
        // Erreurs du navigateur : meme message aux chiffres pres
        envoyer(agent, post("/api/support/erreurs/navigateur"),
                "{\"type\":\"TypeError\",\"message\":\"Cannot read properties of undefined (reading 'id') at 12:5\",\"url\":\"/clients/12\"}")
                .andExpect(status().isNoContent());
        envoyer(agent, post("/api/support/erreurs/navigateur"),
                "{\"type\":\"TypeError\",\"message\":\"Cannot read properties of undefined (reading 'id') at 345:17\",\"url\":\"/clients/345\"}")
                .andExpect(status().isNoContent());
        envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"RangeError\",\"message\":\"Invalid time value\"}")
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/support/erreurs").session(admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.type == 'TypeError')].occurrences").value(2))
                .andExpect(jsonPath("$[?(@.type == 'TypeError')].chemin").value("/clients/345"))
                .andExpect(jsonPath("$[?(@.type == 'TypeError')].utilisateur").value("Test it-agent"));

        // Erreur inattendue du serveur : la reponse 500 reste generique, l'erreur est capturee
        when(segmentService.lister(anyBoolean())).thenThrow(new IllegalStateException("Table crm_segment verrouillee depuis 42 s"))
                .thenThrow(new IllegalStateException("Table crm_segment verrouillee depuis 7 s"));
        mvc.perform(get("/api/segments").session(admin)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("référence")));
        mvc.perform(get("/api/segments").session(admin)).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForMap("select origine, occurrences, chemin from crm_support_erreur where type_erreur = 'java.lang.IllegalStateException'"))
                .containsEntry("origine", "SERVEUR").containsEntry("occurrences", 2).containsEntry("chemin", "GET /api/segments");

        // Plafond par minute : au-dela, les repetitions sont ignorees
        jdbc.update("update crm_parametre set valeur = '3' where cle = 'support.capture_max_minute'");
        for (int i = 0; i < 5; i++) {
            supportDiagnosticService.capturerNavigateur(SupportErreurNavigateurDTO.builder()
                    .type("Error").message("Rafale " + i).build());
        }
        jdbc.update("update crm_parametre set valeur = '10' where cle = 'support.capture_max_minute'");
        assertThat(jdbc.queryForObject("select occurrences from crm_support_erreur where type_erreur = 'Error'", Integer.class)).isEqualTo(3);

        // Traitee, l'erreur sort de la liste ; elle y revient si elle survient de nouveau
        Long id = jdbc.queryForObject("select id from crm_support_erreur where type_erreur = 'RangeError'", Long.class);
        mvc.perform(post("/api/support/erreurs/" + id + "/traitement").session(agent).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/support/erreurs/" + id + "/traitement").session(admin).with(csrf()))
                .andExpect(jsonPath("$.traite").value(true)).andExpect(jsonPath("$.traitePar").isNotEmpty());
        mvc.perform(get("/api/support/erreurs").param("traitees", "true").session(admin)).andExpect(jsonPath("$", hasSize(1)));
        envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"RangeError\",\"message\":\"Invalid time value\"}");
        assertThat(jdbc.queryForObject("select traite from crm_support_erreur where id = ?", Boolean.class, id)).isFalse();

        // Purge selon la retention
        jdbc.update("update crm_support_erreur set derniere_le = utc_timestamp() - interval 100 day where id = ?", id);
        assertThat(supportDiagnosticService.purger()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from crm_support_erreur where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void laSanteRepondSondeParSonde() throws Exception {
        when(passerelle.joignable()).thenReturn(false);
        jdbc.update("insert into crm_session_whatsapp (numero, statut, sante, updated_at) values ('2250500000000', 'CONNECTE', 'OK', utc_timestamp())");
        mvc.perform(get("/api/support/sante").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.code == 'BASE')].etat").value("OK"))
                .andExpect(jsonPath("$[?(@.code == 'BASE')].detail", hasItem(containsString("schéma en version"))))
                .andExpect(jsonPath("$[?(@.code == 'WHATSAPP_WEB')].etat").value("KO"))
                .andExpect(jsonPath("$[?(@.code == 'EMAIL')].etat").value("OK"))
                .andExpect(jsonPath("$[?(@.code == 'SMS')].etat").value("ALERTE"))
                .andExpect(jsonPath("$[?(@.code == 'SERVEUR')].etat").exists());
    }

    @Test
    void laFaqSArchiveSansEtreSupprimee() throws Exception {
        mvc.perform(get("/api/support/faq").session(agent)).andExpect(jsonPath("$", hasSize(2)));
        envoyer(agent, post("/api/support/faq"), "{\"categorie\":\"Factures\",\"question\":\"IT Q\",\"reponse\":\"R\"}")
                .andExpect(status().isForbidden());

        String faq = envoyer(admin, post("/api/support/faq"),
                "{\"categorie\":\"Factures\",\"question\":\"IT Comment relancer une facture ?\",\"reponse\":\"Depuis la fiche client.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.ordre").value(1))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(faq, "$.id")).longValue();
        envoyer(admin, post("/api/support/faq"), "{\"categorie\":\"Autre\",\"question\":\"it comment relancer une facture ?\",\"reponse\":\"x\"}")
                .andExpect(status().isBadRequest());
        envoyer(admin, put("/api/support/faq/" + id), "{\"categorie\":\"Factures\",\"question\":\"IT Comment relancer une facture ?\",\"reponse\":\"Par une campagne.\"}")
                .andExpect(jsonPath("$.reponse").value("Par une campagne."));

        mvc.perform(post("/api/support/faq/" + id + "/archivage").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(get("/api/support/faq").session(agent)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/support/faq/archives").session(agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/faq/archives").session(admin)).andExpect(jsonPath("$[0].id").value(id));
        assertThat(jdbc.queryForObject("select count(*) from crm_support_faq where id = ?", Integer.class, id)).isEqualTo(1);
        mvc.perform(post("/api/support/faq/" + id + "/reactivation").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ACTIF"));
        assertThat(jdbc.queryForList("select action from crm_journal_action where entite = 'crm_support_faq' order by id", String.class))
                .containsExactly("SUPPORT_FAQ_CREATION", "SUPPORT_FAQ_MODIFICATION", "SUPPORT_FAQ_ARCHIVAGE", "SUPPORT_FAQ_REACTIVATION");
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
