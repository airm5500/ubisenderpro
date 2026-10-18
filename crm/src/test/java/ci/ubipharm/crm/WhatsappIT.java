package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.whatsapp.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.EtatSessionDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.ResultatEnvoiDTO;

/** Envoi WhatsApp : liaison des telephones, debit, issues d'envoi, accuses et reponses des clients. Le service wa-web est simule. */
class WhatsappIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final String NUMERO_AGENT = "2250711111111";
    private static final String NUMERO_CLIENT = "2250501010101";
    private static final String JETON = "jeton-de-test";

    @MockitoBean
    PasserelleWhatsappService passerelle;

    @Autowired
    EnvoiWhatsappService envoiWhatsappService;

    private MockHttpSession admin;
    private Long agent1;
    private Long agent2;
    private Long campagne;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent2", "AGENT", "RECOUVREMENT", YOPOUGON);
        agent1 = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent1'", Long.class);
        agent2 = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent2'", Long.class);
        jdbc.update("update crm_utilisateur set numero_whatsapp = ? where id = ?", NUMERO_AGENT, agent1);
        parametre("whatsapp.envoi.heure_debut", "0");
        parametre("whatsapp.envoi.heure_fin", "24");
        parametre("whatsapp.envoi.delai_min_secondes", "0");
        parametre("whatsapp.envoi.delai_max_secondes", "0");
        parametre("whatsapp.envoi.plafond_jour", "200");

        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, agence_id, created_at) "
                + "values ('W-C1', 'Pharmacie W-C1', 'Dr KOUASSI', ?, utc_timestamp())", YOPOUGON);
        Long c1 = jdbc.queryForObject("select id from crm_client where code = 'W-C1'", Long.class);
        jdbc.update("insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, created_at) "
                + "values (?, 'Dr KOUASSI', 'PRINCIPAL', ?, 1, utc_timestamp())", c1, NUMERO_CLIENT);
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c1, agent1, agent1);
        LocalDate echeance = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        for (String reference : new String[] {"W-F1", "W-F2"}) {
            jdbc.update("""
                    insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                    values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, 50000, 50000, utc_timestamp())
                    """, reference, c1, echeance.minusDays(30), echeance);
        }

        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        Long modele = idDe(mvc.perform(post("/api/recouvrement/modeles-messages").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retard\",\"typeCampagne\":\"APRES_ECHEANCE\",\"contenu\":\"Bonjour {nom_client}, facture {reference_facture}\"}")));
        campagne = idDe(mvc.perform(post("/api/recouvrement/campagnes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retards\",\"typeCampagne\":\"APRES_ECHEANCE\",\"modeleId\":" + modele + ",\"criteres\":{}}")));
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/soumission").session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/validation").session(admin).with(csrf()))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
    }

    @Test
    void lesCallbacksExigentLeJetonPartage() throws Exception {
        evenement("status", null, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"status\":\"CONNECTE\"}").andExpect(status().isUnauthorized());
        evenement("status", "faux", "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"status\":\"CONNECTE\"}").andExpect(status().isUnauthorized());
        connecterSession();
        assertThat(jdbc.queryForObject("select statut from crm_session_whatsapp where numero = ?", String.class, NUMERO_AGENT)).isEqualTo("CONNECTE");
        evenement("status", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"status\":\"CONNECTE\",\"health\":\"DEGRADED\",\"reason\":\"Illisible\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/whatsapp/sessions").session(admin))
                .andExpect(jsonPath("$[0].numero").value(NUMERO_AGENT))
                .andExpect(jsonPath("$[0].sante").value("DEGRADED"))
                .andExpect(jsonPath("$[0].enAttente").value(2))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void lAgentLieSonPropreTelephoneParQr() throws Exception {
        when(passerelle.demarrer(NUMERO_AGENT)).thenReturn(EtatSessionDTO.builder().statut("QR").sante("OK").qr("data:image/png;base64,AAA").build());
        MockHttpSession agent = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
        mvc.perform(post("/api/whatsapp/sessions/" + agent1 + "/connexion").session(agent).with(csrf()))
                .andExpect(jsonPath("$.statut").value("QR"))
                .andExpect(jsonPath("$.qr").value("data:image/png;base64,AAA"));
        mvc.perform(post("/api/whatsapp/sessions/" + agent2 + "/connexion").session(agent).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp/sessions/" + agent2 + "/connexion").session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enregistrez d'abord le numero WhatsApp professionnel de l'agent."));
        mvc.perform(get("/api/whatsapp/sessions").session(agent)).andExpect(jsonPath("$[0].statut").value("QR"));
    }

    @Test
    void lesRelancesPartentUneParUneDepuisLeNumeroDeLAgent() throws Exception {
        assertThat(envoiWhatsappService.envoyerLot()).as("telephone pas encore lie").isZero();
        verify(passerelle, never()).envoyerTexte(anyString(), anyString(), anyString());

        connecterSession();
        when(passerelle.envoyerTexte(eq(NUMERO_AGENT), eq(NUMERO_CLIENT), startsWith("Bonjour Dr KOUASSI, facture W-F")))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("WA-1").build(),
                        ResultatEnvoiDTO.builder().succes(true).identifiant("WA-2").build());
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(campagne()).isEqualTo("EN_COURS");
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(envoiWhatsappService.envoyerLot()).isZero();

        assertThat(jdbc.queryForList("select identifiant_whatsapp from crm_campagne_message where statut = 'ENVOYE' order by id", String.class))
                .containsExactly("WA-1", "WA-2");
        assertThat(statutFacture("W-F1")).isEqualTo("RELANCE_ENVOYEE");
        assertThat(campagne()).isEqualTo("TERMINEE");
        mvc.perform(get("/api/recouvrement/campagnes/" + campagne).session(admin))
                .andExpect(jsonPath("$.nbEnvoyes").value(2))
                .andExpect(jsonPath("$.termineLe").exists());

        // Accuses : distribue puis lu, jamais de retour en arriere
        evenement("ack", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"id\":\"WA-1\",\"status\":\"DISTRIBUE\"}").andExpect(status().isNoContent());
        evenement("ack", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"id\":\"WA-2\",\"status\":\"LU\"}");
        evenement("ack", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"id\":\"WA-2\",\"status\":\"DISTRIBUE\"}");
        assertThat(statutMessage("WA-1")).isEqualTo("DISTRIBUE");
        assertThat(statutMessage("WA-2")).isEqualTo("LU");
        assertThat(statutFacture("W-F1")).isEqualTo("MESSAGE_DISTRIBUE");
        assertThat(statutFacture("W-F2")).isEqualTo("MESSAGE_LU");
        assertThat(jdbc.queryForObject("select count(*) from crm_campagne_message where identifiant_whatsapp = 'WA-2' and distribue_le is not null",
                Long.class)).isEqualTo(1);

        // La reponse du client est rattachee a sa derniere relance
        String reponse = "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"from\":\"" + NUMERO_CLIENT
                + "\",\"name\":\"Kouassi\",\"type\":\"TEXTE\",\"text\":\"Je passe payer demain\",\"id\":\"IN-1\"}";
        evenement("message", JETON, reponse).andExpect(status().isNoContent());
        evenement("message", JETON, reponse).andExpect(status().isNoContent());
        evenement("message", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"from\":\"2250999999999\",\"text\":\"Qui est-ce ?\",\"id\":\"IN-2\"}");
        assertThat(statutFacture("W-F2")).isEqualTo("REPONSE_RECUE");
        mvc.perform(get("/api/recouvrement/reponses").session(connecter("it-agent1", MOT_DE_PASSE_ADMIN)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[?(@.contenu == 'Je passe payer demain')].factureReference").value("W-F2"))
                .andExpect(jsonPath("$.elements[?(@.contenu == 'Je passe payer demain')].campagneNom").value("Retards"))
                .andExpect(jsonPath("$.elements[?(@.contenu == 'Qui est-ce ?')].clientId").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get("/api/recouvrement/reponses").session(connecter("it-agent2", MOT_DE_PASSE_ADMIN))).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void lesIssuesDEnvoiSontTraiteesSansDoublon() throws Exception {
        connecterSession();
        // Numero absent de WhatsApp : echec definitif, la facture redevient a relancer
        when(passerelle.envoyerTexte(anyString(), anyString(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().erreur("Numero absent de WhatsApp").build())
                .thenReturn(ResultatEnvoiDTO.builder().sessionIndisponible(true).erreur("Session deconnectee").build());
        envoiWhatsappService.envoyerLot();
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where statut = 'ECHEC'", String.class)).isEqualTo("Numero absent de WhatsApp");
        assertThat(statutFacture("W-F1")).isEqualTo("A_RELANCER");

        // Telephone deconnecte : le message attend, sans essai compte
        envoiWhatsappService.envoyerLot();
        assertThat(jdbc.queryForObject("select tentatives from crm_campagne_message where statut = 'A_ENVOYER'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select statut from crm_session_whatsapp where numero = ?", String.class, NUMERO_AGENT)).isEqualTo("DECONNECTE");
        assertThat(envoiWhatsappService.envoyerLot()).isZero();

        // Service injoignable : trois essais puis echec
        connecterSession();
        when(passerelle.envoyerTexte(anyString(), anyString(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().erreurTechnique(true).erreur("Service WhatsApp injoignable").build());
        envoiWhatsappService.envoyerLot();
        envoiWhatsappService.envoyerLot();
        assertThat(jdbc.queryForObject("select statut from crm_campagne_message where facture_id = (select id from crm_facture where reference = 'W-F2')",
                String.class)).isEqualTo("A_ENVOYER");
        envoiWhatsappService.envoyerLot();
        assertThat(jdbc.queryForObject("select count(*) from crm_campagne_message where statut = 'ECHEC'", Long.class)).isEqualTo(2);
        assertThat(campagne()).isEqualTo("TERMINEE");
        mvc.perform(get("/api/recouvrement/campagnes/" + campagne).session(admin)).andExpect(jsonPath("$.nbEchecs").value(2));
    }

    @Test
    void leDebitRespecteLePlafondLaPlageHoraireEtLesArretsBrutaux() throws Exception {
        connecterSession();
        when(passerelle.envoyerTexte(anyString(), anyString(), anyString())).thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("WA-9").build());
        parametre("whatsapp.envoi.heure_fin", "0");
        assertThat(envoiWhatsappService.envoyerLot()).as("hors plage horaire").isZero();
        parametre("whatsapp.envoi.heure_fin", "24");
        parametre("whatsapp.envoi.plafond_jour", "1");
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(envoiWhatsappService.envoyerLot()).as("plafond du jour atteint").isZero();

        jdbc.update("update crm_campagne_message set statut = 'ENVOI' where statut = 'A_ENVOYER'");
        assertThat(envoiWhatsappService.reprendreEnvoisInterrompus()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where statut = 'ECHEC'", String.class))
                .startsWith("Envoi interrompu");
        assertThat(campagne()).isEqualTo("TERMINEE");
    }

    private void connecterSession() throws Exception {
        evenement("status", JETON, "{\"sessionId\":\"" + NUMERO_AGENT + "\",\"status\":\"CONNECTE\",\"health\":\"OK\"}").andExpect(status().isNoContent());
    }

    private ResultActions evenement(String type, String jeton, String corps) throws Exception {
        var requete = post("/api/passerelle/wa-web/" + type).contentType(MediaType.APPLICATION_JSON).content(corps);
        if (jeton != null) {
            requete.header("X-Api-Token", jeton);
        }
        return mvc.perform(requete);
    }

    private void parametre(String cle, String valeur) {
        jdbc.update("update crm_parametre set valeur = ? where cle = ?", valeur, cle);
    }

    private String campagne() {
        return jdbc.queryForObject("select statut from crm_campagne where id = ?", String.class, campagne);
    }

    private String statutFacture(String reference) {
        return jdbc.queryForObject("select statut from crm_facture where reference = ?", String.class, reference);
    }

    private String statutMessage(String identifiant) {
        return jdbc.queryForObject("select statut from crm_campagne_message where identifiant_whatsapp = ?", String.class, identifiant);
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
