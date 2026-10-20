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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SegmentService;
import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.SupportExperienceService;
import ci.ubipharm.crm.rest.service.dto.SupportErreurNavigateurDTO;
import ci.ubipharm.crm.socle.support.FiltreLenteurs;

/** Centre de support : tickets (dont automatiques), diagnostic des erreurs et des lenteurs, experience utilisateur, sante, export et FAQ. */
class SupportIT extends IntegrationBase {

    @MockitoBean
    PasserelleWhatsappService passerelle;

    /** Sert a provoquer une erreur inattendue (reponse 500) sur un ecran existant. */
    @MockitoBean
    SegmentService segmentService;

    @Autowired
    SupportDiagnosticService supportDiagnosticService;

    @Autowired
    SupportExperienceService supportExperienceService;

    @Autowired
    FiltreLenteurs filtreLenteurs;

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
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[?(@.code == 'POOL')].etat").value("OK"))
                .andExpect(jsonPath("$[?(@.code == 'EXPERIENCE')].detail").value("Aucune mesure reçue depuis une heure."))
                .andExpect(jsonPath("$[?(@.code == 'INCIDENTS')].detail", hasItem(containsString("0 erreur(s) à traiter"))))
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

    @Test
    void uneErreurInattendueOuvreUnTicketAutomatique() throws Exception {
        // Erreur du serveur : ticket des la 1re survenue, rattache a l'erreur ; la 2e survenue n'en ouvre pas d'autre
        when(segmentService.lister(anyBoolean())).thenThrow(new IllegalStateException("Verrou 12 depasse"));
        mvc.perform(get("/api/segments").session(admin)).andExpect(status().isInternalServerError());
        mvc.perform(get("/api/segments").session(admin)).andExpect(status().isInternalServerError());
        Long ticket = jdbc.queryForObject("select ticket_id from crm_support_erreur where type_erreur = 'java.lang.IllegalStateException'", Long.class);
        assertThat(ticket).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from crm_support_ticket", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/support/tickets/tous").session(admin))
                .andExpect(jsonPath("$[0].sujet").value("[Auto] IllegalStateException : Verrou 12 depasse"))
                .andExpect(jsonPath("$[0].auteur").value("Système (automatique)"))
                .andExpect(jsonPath("$[0].automatique").value(true))
                .andExpect(jsonPath("$[0].priorite").value("HAUTE"))
                .andExpect(jsonPath("$[0].categorie").value("INCIDENT"));
        mvc.perform(get("/api/support/tickets/" + ticket).session(admin))
                .andExpect(jsonPath("$.erreurId").isNotEmpty())
                .andExpect(jsonPath("$.messages[0].auteur").value("Système"))
                .andExpect(jsonPath("$.messages[0].contenu", containsString("Écran ou adresse : GET /api/segments")));
        // Le ticket automatique n'est pas lisible par un utilisateur sans droit Support
        mvc.perform(get("/api/support/tickets/" + ticket).session(agent)).andExpect(status().isForbidden());

        // Resolu, il est rouvert si l'erreur revient
        envoyer(admin, post("/api/support/tickets/" + ticket + "/statut"), "{\"statut\":\"RESOLU\"}").andExpect(status().isOk());
        mvc.perform(get("/api/segments").session(admin)).andExpect(status().isInternalServerError());
        mvc.perform(get("/api/support/tickets/" + ticket).session(admin))
                .andExpect(jsonPath("$.statut").value("OUVERT"))
                .andExpect(jsonPath("$.messages[-1:].contenu", hasItem(containsString("survenue de nouveau (3 survenue(s))"))));

        // Erreur du navigateur : ticket a partir du seuil seulement
        jdbc.update("update crm_parametre set valeur = '3' where cle = 'support.ticket_auto_seuil_navigateur'");
        for (int i = 0; i < 2; i++) {
            envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"TypeError\",\"message\":\"x is undefined\",\"url\":\"/factures\"}");
        }
        assertThat(jdbc.queryForObject("select ticket_id from crm_support_erreur where type_erreur = 'TypeError'", Long.class)).isNull();
        envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"TypeError\",\"message\":\"x is undefined\",\"url\":\"/factures\"}");
        assertThat(jdbc.queryForObject("select ticket_id from crm_support_erreur where type_erreur = 'TypeError'", Long.class)).isNotNull();
        jdbc.update("update crm_parametre set valeur = '5' where cle = 'support.ticket_auto_seuil_navigateur'");

        // Plafond quotidien : au-dela, l'erreur reste au diagnostic sans ticket
        jdbc.update("update crm_parametre set valeur = '2' where cle = 'support.ticket_auto_max_jour'");
        supportDiagnosticService.signaler("SURVEILLANCE", "Mémoire du serveur", "La mémoire dépasse le seuil", "détail", null, null);
        jdbc.update("update crm_parametre set valeur = '10' where cle = 'support.ticket_auto_max_jour'");
        Long memoire = jdbc.queryForObject("select id from crm_support_erreur where origine = 'SURVEILLANCE'", Long.class);
        assertThat(jdbc.queryForObject("select ticket_id from crm_support_erreur where id = ?", Long.class, memoire)).isNull();

        // Ticket a la main depuis le diagnostic, une seule fois ; filtre par origine
        mvc.perform(get("/api/support/erreurs").param("origine", "SURVEILLANCE").session(admin)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/support/erreurs/" + memoire + "/ticket").session(agent).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/support/erreurs/" + memoire + "/ticket").session(admin).with(csrf()))
                .andExpect(jsonPath("$.sujet").value("Mémoire du serveur : La mémoire dépasse le seuil"))
                .andExpect(jsonPath("$.auteur").value(nomAdmin()))
                .andExpect(jsonPath("$.erreurId").value(memoire));
        mvc.perform(post("/api/support/erreurs/" + memoire + "/ticket").session(admin).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("déjà son ticket")));

        // Desactive : plus de ticket automatique
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'support.ticket_auto'");
        envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"SyntaxError\",\"message\":\"y\"}");
        supportDiagnosticService.capturerServeur(new IllegalArgumentException("Autre"), "REF");
        jdbc.update("update crm_parametre set valeur = 'OUI' where cle = 'support.ticket_auto'");
        assertThat(jdbc.queryForObject("select count(*) from crm_support_erreur where ticket_id is null and type_erreur in ('SyntaxError', "
                + "'java.lang.IllegalArgumentException')", Integer.class)).isEqualTo(2);
    }

    @Test
    void uneRequeteLenteEstSignaleeSansTicket() throws Exception {
        jdbc.update("update crm_parametre set valeur = '1000' where cle = 'support.lenteur_ms'");
        ReflectionTestUtils.setField(filtreLenteurs, "seuilLuLe", 0L);
        when(segmentService.lister(anyBoolean())).thenAnswer(i -> {
            Thread.sleep(1100);
            return List.of();
        });
        mvc.perform(get("/api/segments").session(agent)).andExpect(status().isOk());
        jdbc.update("update crm_parametre set valeur = '3000' where cle = 'support.lenteur_ms'");
        ReflectionTestUtils.setField(filtreLenteurs, "seuilLuLe", 0L);
        // Enregistree en arriere-plan
        Map<String, Object> lenteur = null;
        for (int i = 0; i < 50 && lenteur == null; i++) {
            List<Map<String, Object>> lignes = jdbc.queryForList("select * from crm_support_erreur where origine = 'LENTEUR'");
            if (lignes.isEmpty()) {
                Thread.sleep(100);
            } else {
                lenteur = lignes.getFirst();
            }
        }
        assertThat(lenteur).isNotNull().containsEntry("type_erreur", "Requête lente").containsEntry("message", "GET /api/segments")
                .containsEntry("ticket_id", null);
        assertThat((String) lenteur.get("detail")).contains("seuil 1000 ms", "Statut HTTP : 200");
        assertThat(lenteur.get("utilisateur_id")).isNotNull();
    }

    @Test
    void lExperienceUtilisateurEstAgregeeParTranche() throws Exception {
        String mesures = """
                [{"type":"API","cle":"GET /api/factures/1234?page=2","nombre":3,"totalMs":4500,"maxMs":3200,"nb1s":2,"nb3s":1,"echecs":1},
                 {"type":"API","cle":"GET /api/factures/98","nombre":1,"totalMs":500,"maxMs":500},
                 {"type":"ECRAN","cle":"  Factures  ","nombre":2,"totalMs":1800,"maxMs":1200,"nb1s":1},
                 {"type":"AUTRE","cle":"x","nombre":5,"totalMs":5},
                 {"type":"API","cle":"GET https://ailleurs.ci/api/x","nombre":1,"totalMs":5},
                 {"type":"API","cle":"POST /api/clients","nombre":-4,"totalMs":5},
                 {"type":"API","cle":"POST /api/clients","nombre":1,"totalMs":99999999,"maxMs":99999999}]
                """;
        envoyer(agent, post("/api/support/rum"), mesures).andExpect(jsonPath("$.actif").value(true));
        envoyer(agent, post("/api/support/rum"), "[{\"type\":\"API\",\"cle\":\"GET /api/factures/7\",\"nombre\":1,\"totalMs\":1000,\"maxMs\":1000,\"nb1s\":1}]");
        // Une ligne par tranche, utilisateur et cle normalisee ; durees bornees a 10 minutes
        assertThat(jdbc.queryForList("select concat(type_mesure, ' ', cle, ' ', nombre, ' ', total_ms, ' ', max_ms, ' ', nb_1s, ' ', echecs) "
                + "from crm_support_rum order by type_mesure, cle", String.class))
                .containsExactly("API GET /api/factures/{id} 5 6000 3200 3 1", "API POST /api/clients 1 600000 600000 0 0",
                        "ECRAN Factures 2 1800 1200 1 0");

        mvc.perform(get("/api/support/experience").session(agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/experience").param("heures", "6").session(admin))
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.resume[?(@.cle == 'API')].nombre").value(6))
                .andExpect(jsonPath("$.appels[0].cle").value("POST /api/clients"))
                .andExpect(jsonPath("$.appels[1].moyenneMs").value(1200))
                .andExpect(jsonPath("$.appels[1].part1s").value(60.0))
                .andExpect(jsonPath("$.echecs", hasSize(1)))
                .andExpect(jsonPath("$.ecrans[0].cle").value("Factures"))
                .andExpect(jsonPath("$.utilisateurs[0].utilisateur").value("Test it-agent"))
                .andExpect(jsonPath("$.utilisateurs[0].ecrans").value(2))
                .andExpect(jsonPath("$.serie", hasSize(1)));
        mvc.perform(get("/api/support/sante").session(admin))
                .andExpect(jsonPath("$[?(@.code == 'EXPERIENCE')].detail", hasItem(containsString("6 appel(s)"))));

        // Desactivee : le navigateur est prie de cesser, rien n'est enregistre
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'support.rum_actif'");
        envoyer(agent, post("/api/support/rum"), "[{\"type\":\"ECRAN\",\"cle\":\"Clients\",\"nombre\":1,\"totalMs\":10}]")
                .andExpect(jsonPath("$.actif").value(false));
        jdbc.update("update crm_parametre set valeur = 'OUI' where cle = 'support.rum_actif'");
        assertThat(jdbc.queryForObject("select count(*) from crm_support_rum where cle = 'Clients'", Integer.class)).isZero();

        // Purge selon la retention
        jdbc.update("update crm_support_rum set tranche = tranche - interval 20 day where type_mesure = 'ECRAN'");
        assertThat(supportExperienceService.purger()).isEqualTo(1);
    }

    @Test
    void leDossierDeDiagnosticSExporteEnZip() throws Exception {
        envoyer(agent, post("/api/support/erreurs/navigateur"), "{\"type\":\"TypeError\",\"message\":\"a; b \\\"c\\\"\"}");
        mvc.perform(get("/api/support/diagnostic/export").session(agent)).andExpect(status().isForbidden());
        byte[] zip = mvc.perform(get("/api/support/diagnostic/export").session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("diagnostic-crm-")))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, String> fichiers = new java.util.LinkedHashMap<>();
        try (ZipInputStream entree = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = entree.getNextEntry(); e != null; e = entree.getNextEntry()) {
                fichiers.put(e.getName(), new String(entree.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(fichiers).containsKeys("info.txt", "erreurs.csv", "tickets.csv", "experience.csv");
        assertThat(fichiers.get("info.txt")).contains("Base de données : OK");
        assertThat(fichiers.get("erreurs.csv")).startsWith("﻿Origine;").contains("NAVIGATEUR;TypeError;\"a; b \"\"c\"\"\"");
        assertThat(jdbc.queryForList("select action from crm_journal_action where action = 'SUPPORT_EXPORT'", String.class)).hasSize(1);
    }

    private String nomAdmin() {
        return jdbc.queryForObject("select nom_complet from crm_utilisateur where login = 'admin'", String.class);
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
