package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.recouvrement.rest.service.CampagneService;

/** Campagnes : modeles et variables, recapitulatif, validation, programmation, annulation et cloisonnement. */
class CampagneIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final String TEXTE = "Bonjour {nom_client}, la facture {reference_facture} de {solde} FCFA est echue depuis {jours_retard} jours. {agent_nom}";

    @Autowired
    CampagneService campagneService;

    private MockHttpSession admin;
    private MockHttpSession agent;
    private Long agent1;
    private Long c1;
    private Long modeleRetard;
    private final LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent2", "AGENT", "RECOUVREMENT", YOPOUGON);
        agent1 = id("crm_utilisateur", "login", "it-agent1");
        Long agent2 = id("crm_utilisateur", "login", "it-agent2");
        jdbc.update("update crm_utilisateur set numero_whatsapp = '2250711111111' where id = ?", agent1);

        c1 = client("K-C1", "Dr KOUASSI Aya", "2250501010101");
        Long c2 = client("K-C2", "Dr BAMBA", null);
        Long c3 = client("K-C3", "Dr KONE", "2250503030303");
        Long c4 = client("K-C4", "Dr YAO", "2250504040404");
        affecter(c1, agent1);
        affecter(c2, agent1);
        affecter(c3, agent2);
        facture("K-F1", c1, aujourdhui.minusDays(10), "150000.00");
        facture("K-F2", c1, aujourdhui.minusDays(3), "20000.50");
        facture("K-F3", c2, aujourdhui.minusDays(5), "10000.00");
        facture("K-F4", c3, aujourdhui.minusDays(5), "10000.00");
        facture("K-F5", c4, aujourdhui.minusDays(5), "10000.00");
        facture("K-F6", c1, aujourdhui.plusDays(3), "30000.00");
        facture("K-F7", c1, aujourdhui.plusDays(7), "30000.00");
        facture("K-F8", c1, aujourdhui.minusDays(20), "0.00");

        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        agent = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
        modeleRetard = idDe(creerModele(admin, "Relance retard", "APRES_ECHEANCE", TEXTE).andExpect(status().isOk()));
    }

    @Test
    void seuleLaDirectionEcritLesModelesEtLesVariablesSontControlees() throws Exception {
        creerModele(admin, "Mauvais", "APRES_ECHEANCE", "Bonjour {nom_client} {inconnue}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variable inconnue : {inconnue}."));
        creerModele(agent, "Le mien", "APRES_ECHEANCE", "Bonjour").andExpect(status().isForbidden());
        mvc.perform(get("/api/recouvrement/modeles-messages").session(agent))
                .andExpect(jsonPath("$[0].variables", contains("nom_client", "reference_facture", "solde", "jours_retard", "agent_nom")));

        // Une colonne de la base devient une variable sans developpement
        mvc.perform(get("/api/recouvrement/variables/sources").session(admin))
                .andExpect(jsonPath("$[?(@.source == 'client.ville')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.source == 'client.id')]", hasSize(0)));
        variable("ville", "client.ville").andExpect(status().isOk());
        variable("pirate", "client.mot_de_passe; drop table x").andExpect(status().isBadRequest());
        creerModele(admin, "Avec ville", "PERSONNALISEE", "Agence de {ville}").andExpect(status().isOk());

        Long idSolde = id("crm_variable_message", "code", "solde");
        mvc.perform(post("/api/recouvrement/variables/" + idSolde + "/archivage").session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La variable {solde} est utilisee par Relance retard."));
    }

    @Test
    void leRecapitulatifSuitLePerimetreDuCreateurEtCompteLesExclusions() throws Exception {
        Long campagneAgent = idDe(creerCampagne(agent, "Retards agent", "APRES_ECHEANCE", modeleRetard, "{}", null));
        mvc.perform(get("/api/recouvrement/campagnes/" + campagneAgent + "/recapitulatif").session(agent))
                .andExpect(jsonPath("$.nbClients").value(1))
                .andExpect(jsonPath("$.nbMessages").value(2))
                .andExpect(jsonPath("$.nbExclus").value(1))
                .andExpect(jsonPath("$.totalSolde").value(170000.50))
                .andExpect(jsonPath("$.exclusions[0].motif").value("Client sans numéro WhatsApp"))
                .andExpect(jsonPath("$.agents[0].numeroExpediteur").value("2250711111111"))
                .andExpect(jsonPath("$.exemples[0].contenu").value(
                        "Bonjour Dr KOUASSI Aya, la facture K-F1 de 150 000 FCFA est echue depuis 10 jours. Test it-agent1"))
                .andExpect(jsonPath("$.exemples[1].contenu").value(
                        "Bonjour Dr KOUASSI Aya, la facture K-F2 de 20 000,5 FCFA est echue depuis 3 jours. Test it-agent1"));

        Long campagneAdmin = idDe(creerCampagne(admin, "Retards", "APRES_ECHEANCE", modeleRetard, "{}", null));
        mvc.perform(get("/api/recouvrement/campagnes/" + campagneAdmin + "/recapitulatif").session(admin))
                .andExpect(jsonPath("$.nbMessages").value(2))
                .andExpect(jsonPath("$.exclusions[*].motif", containsInAnyOrder("Client sans numéro WhatsApp",
                        "Agent Test it-agent2 sans numéro WhatsApp", "Client sans agent de recouvrement")));
        mvc.perform(get("/api/recouvrement/campagnes/" + campagneAdmin + "/messages").param("statut", "EXCLU").session(admin))
                .andExpect(jsonPath("$.total").value(3));

        // Le brouillon d'un autre reste invisible a un agent
        mvc.perform(get("/api/recouvrement/campagnes/" + campagneAdmin).session(agent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/recouvrement/campagnes").session(connecter("it-agent2", MOT_DE_PASSE_ADMIN)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void lesFenetresAvantEtJourDEcheanceSontRespectees() throws Exception {
        Long modeleAvant = idDe(creerModele(admin, "Rappel", "AVANT_ECHEANCE", "{reference_facture} dans {jours_avant_echeance} jours"));
        Long avant = idDe(creerCampagne(admin, "J-5 a J-1", "AVANT_ECHEANCE", modeleAvant, "{}", null));
        mvc.perform(get("/api/recouvrement/campagnes/" + avant + "/messages").session(admin))
                .andExpect(jsonPath("$.elements[*].contenu", contains("K-F6 dans 3 jours")));
        creerCampagne(admin, "Inverse", "AVANT_ECHEANCE", modeleAvant, "{\"joursAvantMin\":4,\"joursAvantMax\":2}", null)
                .andExpect(status().isBadRequest());
        creerCampagne(admin, "Mauvais modele", "JOUR_ECHEANCE", modeleAvant, "{}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le modèle « Rappel » est prévu pour un autre type de relance."));
        Long cible = idDe(creerCampagne(admin, "Ciblee", "PERSONNALISEE", modeleAvant,
                "{\"clientIds\":[" + c1 + "],\"soldeMin\":25000}", null));
        mvc.perform(get("/api/recouvrement/campagnes/" + cible + "/messages").session(admin))
                .andExpect(jsonPath("$.elements[*].factureReference", contains("K-F1", "K-F6", "K-F7")));
    }

    @Test
    void laValidationFigeLesMessagesEtLAnnulationLesRetire() throws Exception {
        Long id = idDe(creerCampagne(agent, "Retards agent", "APRES_ECHEANCE", modeleRetard, "{}", null));
        action(agent, id, "validation", null).andExpect(status().isForbidden());
        action(admin, id, "validation", null).andExpect(status().isBadRequest());
        action(agent, id, "soumission", null).andExpect(jsonPath("$.statut").value("A_VALIDER")).andExpect(jsonPath("$.nbMessages").value(2));

        action(admin, id, "rejet", "Ajoutez le numero de K-C2").andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.motifRejet").value("Ajoutez le numero de K-C2"));
        action(agent, id, "soumission", null);
        action(admin, id, "validation", null)
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.validePar").value("Administrateur"))
                .andExpect(jsonPath("$.lanceLe").exists());
        assertThat(jdbc.queryForList("select statut from crm_campagne_message where campagne_id = ? order by statut", String.class, id))
                .containsExactly("A_ENVOYER", "A_ENVOYER", "EXCLU");
        assertThat(statutFacture("K-F1")).isEqualTo("RELANCE_PROGRAMMEE");

        // Le texte fige ne bouge plus si le modele change
        mvc.perform(put("/api/recouvrement/modeles-messages/" + modeleRetard).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Relance retard\",\"typeCampagne\":\"APRES_ECHEANCE\",\"contenu\":\"Nouveau texte\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/recouvrement/campagnes/" + id + "/messages").param("statut", "A_ENVOYER").session(agent))
                .andExpect(jsonPath("$.elements[0].contenu", org.hamcrest.Matchers.startsWith("Bonjour Dr KOUASSI Aya")));
        mvc.perform(put("/api/recouvrement/campagnes/" + id).session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(demande("Autre", "APRES_ECHEANCE", modeleRetard, "{}", null))).andExpect(status().isBadRequest());

        // Une facture deja en attente d'envoi n'est pas relancee deux fois
        Long doublon = idDe(creerCampagne(admin, "Doublon", "APRES_ECHEANCE", modeleRetard, "{\"clientIds\":[" + c1 + "]}", null));
        mvc.perform(get("/api/recouvrement/campagnes/" + doublon + "/recapitulatif").session(admin))
                .andExpect(jsonPath("$.nbMessages").value(0))
                .andExpect(jsonPath("$.exclusions[0].motif").value("Déjà dans la campagne « Retards agent » en attente d'envoi"))
                .andExpect(jsonPath("$.exclusions[0].nb").value(2));
        action(admin, doublon, "soumission", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La campagne ne contient aucun message à envoyer."));

        action(agent, id, "annulation", "Erreur").andExpect(status().isBadRequest());
        action(admin, id, "annulation", "Client injoignable").andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.motifAnnulation").value("Client injoignable"));
        assertThat(jdbc.queryForObject("select count(*) from crm_campagne_message where campagne_id = ? and statut = 'ANNULE'", Long.class, id))
                .isEqualTo(2);
        assertThat(statutFacture("K-F1")).isEqualTo("A_RELANCER");
        assertThat(jdbc.queryForList("select action from crm_journal_action where entite = 'crm_campagne' and entite_id = ? order by id", String.class, id))
                .containsExactly("CAMPAGNE_CREATION", "CAMPAGNE_SOUMISSION", "CAMPAGNE_REJET", "CAMPAGNE_SOUMISSION", "CAMPAGNE_VALIDATION",
                        "CAMPAGNE_LANCEMENT", "CAMPAGNE_ANNULATION");
    }

    @Test
    void uneCampagneProgrammeeDemarreASonHeureSansLesFacturesSoldees() throws Exception {
        String demain = LocalDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0).toString();
        Long id = idDe(creerCampagne(admin, "Programmee", "PERSONNALISEE", modeleRetard, "{\"clientIds\":[" + c1 + "],\"joursRetardMin\":1}", demain));
        action(admin, id, "soumission", null);
        action(admin, id, "validation", null).andExpect(jsonPath("$.statut").value("VALIDEE")).andExpect(jsonPath("$.lanceLe").doesNotExist());
        assertThat(campagneService.demarrerCampagnesEchues()).isZero();

        jdbc.update("update crm_facture set solde = 0, statut = 'SOLDEE' where reference = 'K-F2'");
        jdbc.update("update crm_campagne set date_execution = utc_timestamp() - interval 1 minute where id = ?", id);
        assertThat(campagneService.demarrerCampagnesEchues()).isEqualTo(1);
        mvc.perform(get("/api/recouvrement/campagnes/" + id).session(admin)).andExpect(jsonPath("$.statut").value("EN_COURS"));
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message m join crm_facture f on f.id = m.facture_id where f.reference = 'K-F2'",
                String.class)).isEqualTo("Facture soldee avant l'envoi");
        assertThat(jdbc.queryForObject("select login from crm_journal_action where action = 'CAMPAGNE_LANCEMENT' and entite_id = ?", String.class, id))
                .isEqualTo("systeme");

        // Validee, l'agent du client la voit avec ses seuls messages
        mvc.perform(get("/api/recouvrement/campagnes").session(agent)).andExpect(jsonPath("$.elements[*].nom", contains("Programmee")));
        mvc.perform(get("/api/recouvrement/campagnes/" + id + "/messages").session(connecter("it-agent2", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isNotFound());
        creerCampagne(admin, "Passee", "PERSONNALISEE", modeleRetard, "{}", "2020-01-01T08:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La date d'exécution est déjà passée."));
    }

    private ResultActions creerModele(MockHttpSession session, String nom, String type, String contenu) throws Exception {
        return mvc.perform(post("/api/recouvrement/modeles-messages").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"" + nom + "\",\"typeCampagne\":\"" + type + "\",\"contenu\":\"" + contenu + "\"}"));
    }

    private ResultActions variable(String code, String source) throws Exception {
        return mvc.perform(post("/api/recouvrement/variables").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"libelle\":\"" + code + "\",\"source\":\"" + source + "\"}"));
    }

    private ResultActions creerCampagne(MockHttpSession session, String nom, String type, Long modeleId, String criteres, String date) throws Exception {
        return mvc.perform(post("/api/recouvrement/campagnes").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(demande(nom, type, modeleId, criteres, date)));
    }

    private String demande(String nom, String type, Long modeleId, String criteres, String date) {
        return "{\"nom\":\"" + nom + "\",\"typeCampagne\":\"" + type + "\",\"modeleId\":" + modeleId + ",\"criteres\":" + criteres
                + (date == null ? "" : ",\"dateExecution\":\"" + date + "\"") + "}";
    }

    private ResultActions action(MockHttpSession session, Long id, String action, String motif) throws Exception {
        var requete = post("/api/recouvrement/campagnes/" + id + "/" + action).session(session).with(csrf());
        if (motif != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"" + motif + "\"}");
        }
        return mvc.perform(requete);
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long client(String code, String pharmacien, String numero) {
        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, agence_id, ville, created_at) "
                + "values (?, ?, ?, ?, 'Abidjan', utc_timestamp())", code, "Pharmacie " + code, pharmacien, YOPOUGON);
        Long id = id("crm_client", "code", code);
        if (numero != null) {
            jdbc.update("insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, created_at) "
                    + "values (?, ?, 'PRINCIPAL', ?, 1, utc_timestamp())", id, pharmacien, numero);
        }
        return id;
    }

    private void affecter(Long clientId, Long agentId) {
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)",
                clientId, agentId, agentId);
    }

    private void facture(String reference, Long clientId, LocalDate echeance, String solde) {
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, statut, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, ?, ?, ?, utc_timestamp())
                """, reference, clientId, echeance.minusDays(30), echeance, solde, solde, "0.00".equals(solde) ? "SOLDEE" : "A_RELANCER");
    }

    private String statutFacture(String reference) {
        return jdbc.queryForObject("select statut from crm_facture where reference = ?", String.class, reference);
    }

    private Long id(String table, String colonne, String valeur) {
        return jdbc.queryForObject("select id from " + table + " where " + colonne + " = ?", Long.class, valeur);
    }
}
