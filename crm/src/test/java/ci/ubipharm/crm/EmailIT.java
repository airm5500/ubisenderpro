package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.EnvoiEmailService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** Relances par e-mail : modele avec objet, exclusions propres au canal, envoi avec l'agent en reponse. */
class EmailIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("crm@ubipharm.ci", "secret"));

    @Autowired
    EnvoiEmailService envoiEmailService;

    private MockHttpSession admin;
    private Long modele;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        Long agent1 = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent1'", Long.class);
        jdbc.update("update crm_utilisateur set email = 'agent1@ubipharm.ci' where id = ?", agent1);
        parametre("email.envoi.heure_debut", "0");
        parametre("email.envoi.heure_fin", "24");
        parametre("email.envoi.delai_secondes", "0");
        parametre("email.expediteur", "relances@ubipharm.ci");

        Long avecEmail = client("E-C1", "Contact@Pharmacie-Plateau.ci; compta@plateau.ci");
        Long sansEmail = client("E-C2", null);
        Long emailInvalide = client("E-C3", "pas une adresse");
        for (Long c : new Long[] {avecEmail, sansEmail, emailInvalide}) {
            jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c, agent1, agent1);
        }
        facture("E-F1", avecEmail);
        facture("E-F2", sansEmail);
        facture("E-F3", emailInvalide);

        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        modele = idDe(modele("{\"nom\":\"Retard e-mail\",\"typeCampagne\":\"APRES_ECHEANCE\",\"canal\":\"EMAIL\","
                + "\"objet\":\"Facture {reference_facture} échue\",\"contenu\":\"Bonjour {nom_client},\\nla facture {reference_facture} reste due.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("EMAIL")));
    }

    @Test
    void unModeleEmailExigeUnObjetEtSeFiltreParCanal() throws Exception {
        modele("{\"nom\":\"Sans objet\",\"typeCampagne\":\"APRES_ECHEANCE\",\"canal\":\"EMAIL\",\"contenu\":\"Bonjour\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saisissez l'objet de l'e-mail."));
        modele("{\"nom\":\"Objet faux\",\"typeCampagne\":\"APRES_ECHEANCE\",\"canal\":\"EMAIL\",\"objet\":\"{inconnue}\",\"contenu\":\"Bonjour\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variable inconnue : {inconnue}."));
        mvc.perform(get("/api/recouvrement/modeles-messages").param("canal", "WHATSAPP").session(admin))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/recouvrement/modeles-messages").param("canal", "EMAIL").session(admin))
                .andExpect(jsonPath("$[0].objet").value("Facture {reference_facture} échue"));
    }

    @Test
    void laCampagneEmailExclutLesClientsSansAdresseEtPartAvecLAgentEnReponse() throws Exception {
        Long campagne = idDe(mvc.perform(post("/api/recouvrement/campagnes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retards e-mail\",\"typeCampagne\":\"APRES_ECHEANCE\",\"modeleId\":" + modele + ",\"criteres\":{}}"))
                .andExpect(jsonPath("$.canal").value("EMAIL")));
        mvc.perform(get("/api/recouvrement/campagnes/" + campagne + "/recapitulatif").session(admin))
                .andExpect(jsonPath("$.nbMessages").value(1))
                .andExpect(jsonPath("$.nbExclus").value(2))
                .andExpect(jsonPath("$.exemples[0].emailDestinataire").value("contact@pharmacie-plateau.ci"))
                .andExpect(jsonPath("$.exemples[0].objet").value("Facture E-F1 échue"))
                .andExpect(jsonPath("$.exclusions[*].motif").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Client sans adresse e-mail", "Adresse e-mail du client invalide")));

        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/soumission").session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/validation").session(admin).with(csrf()))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
        mvc.perform(get("/api/email/etat").session(admin))
                .andExpect(jsonPath("$.configure").value(true))
                .andExpect(jsonPath("$.expediteur").value("relances@ubipharm.ci"))
                .andExpect(jsonPath("$.enAttente").value(1));

        assertThat(envoiEmailService.envoyerSuivant()).isEqualTo(1);
        assertThat(envoiEmailService.envoyerSuivant()).isZero();

        MimeMessage[] recus = SMTP.getReceivedMessages();
        assertThat(recus).hasSize(1);
        MimeMessage recu = recus[0];
        assertThat(recu.getSubject()).isEqualTo("Facture E-F1 échue");
        assertThat(((InternetAddress) recu.getFrom()[0]).getAddress()).isEqualTo("relances@ubipharm.ci");
        assertThat(((InternetAddress) recu.getReplyTo()[0]).getAddress()).isEqualTo("agent1@ubipharm.ci");
        assertThat(((InternetAddress) recu.getAllRecipients()[0]).getAddress()).isEqualTo("contact@pharmacie-plateau.ci");
        assertThat(GreenMailUtil.getBody(recu)).contains("la facture E-F1 reste due.");

        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'E-F1'", String.class)).isEqualTo("RELANCE_ENVOYEE");
        assertThat(jdbc.queryForObject("select statut from crm_campagne where id = ?", String.class, campagne)).isEqualTo("TERMINEE");
        assertThat(jdbc.queryForObject("select nb_envoyes from crm_campagne where id = ?", Integer.class, campagne)).isEqualTo(1);
    }

    @Test
    void unServeurInjoignableFaitReessayerPuisEchouer() throws Exception {
        Long campagne = idDe(mvc.perform(post("/api/recouvrement/campagnes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retards e-mail\",\"typeCampagne\":\"APRES_ECHEANCE\",\"modeleId\":" + modele + ",\"criteres\":{}}")));
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/soumission").session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/validation").session(admin).with(csrf())).andExpect(status().isOk());
        SMTP.stop();

        envoiEmailService.envoyerSuivant();
        assertThat(jdbc.queryForObject("select statut from crm_campagne_message where campagne_id = ? and statut <> 'EXCLU'", String.class, campagne))
                .isEqualTo("A_ENVOYER");
        envoiEmailService.envoyerSuivant();
        envoiEmailService.envoyerSuivant();
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where campagne_id = ? and statut = 'ECHEC'", String.class, campagne))
                .isEqualTo("Serveur de messagerie injoignable");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'E-F1'", String.class)).isEqualTo("A_RELANCER");
    }

    private ResultActions modele(String json) throws Exception {
        return mvc.perform(post("/api/recouvrement/modeles-messages").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long client(String code, String email) {
        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, email, agence_id, created_at) values (?, ?, ?, ?, ?, utc_timestamp())",
                code, "Pharmacie " + code, "Dr " + code, email, YOPOUGON);
        return jdbc.queryForObject("select id from crm_client where code = ?", Long.class, code);
    }

    private void facture(String reference, Long clientId) {
        LocalDate echeance = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, 50000, 50000, utc_timestamp())
                """, reference, clientId, echeance.minusDays(30), echeance);
    }

    private void parametre(String cle, String valeur) {
        jdbc.update("update crm_parametre set valeur = ? where cle = ?", valeur, cle);
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
