package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** Envoi WhatsApp en masse, discussions, historique des envois, historique d'un compte. */
class EchangesIT extends IntegrationBase {

    private static final String NUMERO = "2250722222222";

    @MockitoBean
    PasserelleWhatsappService passerelle;

    @Autowired
    EnvoiMasseService envoiMasseService;

    @Autowired
    EvenementWhatsappService evenementWhatsappService;

    private MockHttpSession admin;
    private MockHttpSession charge;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        for (String[] p : new String[][] {{"whatsapp.envoi.heure_debut", "0"}, {"whatsapp.envoi.heure_fin", "24"},
                {"whatsapp.envoi.delai_min_secondes", "0"}, {"whatsapp.envoi.delai_max_secondes", "0"}, {"whatsapp.envoi.plafond_jour", "200"},
                {"discussions.reponse_active", "OUI"}, {"whatsapp.envoi_masse.heure_debut", "0"}, {"whatsapp.envoi_masse.heure_fin", "24"}}) {
            jdbc.update("update crm_parametre set valeur = ? where cle = ?", p[1], p[0]);
        }
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        jdbc.update("update crm_utilisateur set numero_whatsapp = ? where login = 'it-marketing'", NUMERO);
        charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);
        jdbc.update("insert into crm_session_whatsapp (numero, statut, sante, updated_at) values (?, 'CONNECTE', 'OK', utc_timestamp())", NUMERO);
        envoyer(admin, post("/api/clients"), """
                {"code":"E-1","raisonSociale":"Pharmacie Un","agenceId":1,"numeros":{"PRINCIPAL":"0700000001"}}""").andExpect(status().isOk());
        Long stop = idDe(envoyer(admin, post("/api/clients"), """
                {"code":"E-2","raisonSociale":"Pharmacie Stop","agenceId":1,"numeros":{"PRINCIPAL":"0700000002"}}"""));
        envoyer(admin, post("/api/marketing/clients/" + stop + "/desabonnement"), "{\"motif\":\"STOP\"}").andExpect(status().isOk());
        Long ferme = idDe(envoyer(admin, post("/api/clients"), """
                {"code":"E-3","raisonSociale":"Pharmacie Fermee","agenceId":1,"numeros":{"PRINCIPAL":"0700000003"}}"""));
        envoyer(admin, post("/api/clients/" + ferme + "/desactivation"), "{\"motif\":\"Fermée\"}").andExpect(status().isOk());
    }

    @Test
    void unEnvoiEnMasseExclutLesDesabonnesPartAuDebitDesRelancesEtSeRetrouveDansLHistorique() throws Exception {
        String numeros = "0700000001\n07 00 00 00 09;Dr Kouassi\n0700000002\n0700000003\n123\n0700000009";
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Rappel\",\"contenu\":\"Bonjour {nom}, nouvel arrivage.\",\"numeros\":\"%s\",\"apercu\":true}"
                .formatted(numeros.replace("\n", "\\n")))
                .andExpect(jsonPath("$.nbRetenus").value(2))
                .andExpect(jsonPath("$.exemple").value("Bonjour Pharmacie Un, nouvel arrivage."))
                .andExpect(jsonPath("$.exclus[*].motif").value(org.hamcrest.Matchers.containsInAnyOrder("Désabonné (STOP)", "Client désactivé",
                        "Format invalide", "En double")));
        assertThat(jdbc.queryForObject("select count(*) from crm_envoi_masse", Integer.class)).isZero();

        Long envoi = ((Number) JsonPath.read(envoyer(charge, post("/api/envois-masse"),
                "{\"nom\":\"Rappel\",\"contenu\":\"Bonjour {nom}, nouvel arrivage.\",\"numeros\":\"%s\"}".formatted(numeros.replace("\n", "\\n")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.envoi.id")).longValue();

        when(passerelle.envoyerTexte(eq(NUMERO), anyString(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("M-1").build(), ResultatEnvoiDTO.builder().succes(true).identifiant("M-2").build());
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        assertThat(envoiMasseService.envoyerLot()).isZero();
        mvc.perform(get("/api/envois-masse/" + envoi).session(charge))
                .andExpect(jsonPath("$.statut").value("TERMINE"))
                .andExpect(jsonPath("$.nbEnvoyes").value(2))
                .andExpect(jsonPath("$.nbExclus").value(4));
        assertThat(jdbc.queryForObject("select contenu from crm_message_sortant where numero_destinataire = '2250700000009'", String.class))
                .isEqualTo("Bonjour Dr Kouassi, nouvel arrivage.");

        // Accuse de lecture, puis reponse du client : la discussion s'ouvre
        evenementWhatsappService.accuse(EvenementAccuseDTO.builder().sessionId(NUMERO).id("M-1").status("LU").build());
        evenementWhatsappService.message(EvenementMessageDTO.builder().sessionId(NUMERO).id("R-1").from("2250700000009").name("Kouassi")
                .type("TEXTE").text("Merci, je passe commande").build());

        mvc.perform(get("/api/historique-envois").param("origine", "ENVOI_MASSE").session(charge))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.elements[?(@.destinataire == '2250700000001')].statut").value("LU"));
        mvc.perform(get("/api/historique-envois/export").param("origine", "ENVOI_MASSE").session(charge))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Désabonné (STOP)")));

        mvc.perform(get("/api/discussions").session(charge))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].numeroClient").value("2250700000009"))
                .andExpect(jsonPath("$.elements[0].nomAffiche").value("Kouassi"))
                .andExpect(jsonPath("$.elements[0].dernierSens").value("ENTRANT"))
                .andExpect(jsonPath("$.elements[0].peutRepondre").value(true));
        when(passerelle.envoyerTexte(NUMERO, "2250700000009", "Bien reçu, merci."))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("M-3").build());
        envoyer(charge, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Bien reçu, merci.\"}"
                .formatted(NUMERO)).andExpect(jsonPath("$.origine").value("DISCUSSION"));
        mvc.perform(get("/api/discussions/messages").param("numeroAgent", NUMERO).param("numeroClient", "2250700000009").session(charge))
                .andExpect(jsonPath("$[*].sens").value(org.hamcrest.Matchers.contains("SORTANT", "ENTRANT", "SORTANT")))
                .andExpect(jsonPath("$[2].contenu").value("Bien reçu, merci."));

        // L'administrateur n'a pas ce numero : il lit, il ne repond pas
        envoyer(admin, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Test\"}".formatted(NUMERO))
                .andExpect(jsonPath("$.message").value("Vous ne pouvez répondre que depuis votre numéro ou celui d'un agent que vous remplacez."));
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'discussions.reponse_active'");
        envoyer(charge, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Test\"}".formatted(NUMERO))
                .andExpect(jsonPath("$.message", containsString("désactivée")));

        // L'annulation arrete ce qui n'est pas parti
        Long second = ((Number) JsonPath.read(envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Second\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andReturn().getResponse().getContentAsString(), "$.envoi.id")).longValue();
        envoyer(charge, post("/api/envois-masse/" + second + "/annulation"), "").andExpect(jsonPath("$.statut").value("ANNULE"));
        assertThat(envoiMasseService.envoyerLot()).isZero();
    }

    @Test
    void lAdministrateurConsulteLesConnexionsEtLesActionsDUnCompte() throws Exception {
        Long id = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-marketing'", Long.class);
        mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"it-marketing\",\"motDePasse\":\"faux\"}"));
        envoyer(charge, post("/api/marketing/listes"), "{\"nom\":\"Tous\",\"parCriteres\":true,\"fonctions\":[\"PRINCIPAL\"]}").andExpect(status().isOk());
        mvc.perform(get("/api/utilisateurs/" + id + "/connexions").session(admin))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].succes").value(false));
        mvc.perform(get("/api/utilisateurs/" + id + "/connexions").param("succes", "true").session(admin)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/utilisateurs/" + id + "/actions").param("action", "liste").session(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].action", containsString("LISTE")));
        mvc.perform(get("/api/utilisateurs/" + id + "/actions").session(charge)).andExpect(status().isForbidden());
    }

    @Test
    void lEnvoiEnMasseASaProprePlageEtNeDependPasDeCelleDesRelances() throws Exception {
        // Plage des relances fermee : l'envoi en masse part quand meme (sa plage est ouverte par defaut)
        jdbc.update("update crm_parametre set valeur = '0' where cle in ('whatsapp.envoi.heure_debut', 'whatsapp.envoi.heure_fin')");
        when(passerelle.envoyerTexte(eq(NUMERO), anyString(), anyString())).thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("P-1").build());
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Nuit\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andExpect(jsonPath("$.horsPlage").value(false));
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        // Sa propre plage fermee : il attend et l'ecran le dit
        jdbc.update("update crm_parametre set valeur = '0' where cle in ('whatsapp.envoi_masse.heure_debut', 'whatsapp.envoi_masse.heure_fin')");
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Nuit 2\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andExpect(jsonPath("$.horsPlage").value(true)).andExpect(jsonPath("$.plageHoraire").value("0 h et 0 h"));
        assertThat(envoiMasseService.envoyerLot()).isZero();
    }

    @Test
    void uneNouvelleDiscussionPartDuNumeroDeLUtilisateurEtApparaitDansLaListe() throws Exception {
        mvc.perform(get("/api/discussions/numeros").session(charge)).andExpect(jsonPath("$[0]").value(NUMERO));
        when(passerelle.envoyerTexte(NUMERO, "2250700000007", "Bonjour Docteur"))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("N-1").build());
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"07 00 00 00 07\",\"contenu\":\"Bonjour Docteur\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu").value("Bonjour Docteur"));
        mvc.perform(get("/api/discussions").session(charge))
                .andExpect(jsonPath("$.elements[0].numeroClient").value("2250700000007"))
                .andExpect(jsonPath("$.elements[0].peutRepondre").value(true));
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"123\",\"contenu\":\"Test\"}")
                .andExpect(jsonPath("$.message", containsString("n'est pas un numéro WhatsApp valide")));
        // L'administrateur de test n'a pas de numero relie
        jdbc.update("update crm_utilisateur set numero_whatsapp = null where login = 'admin'");
        envoyer(admin, post("/api/discussions"), "{\"numeroClient\":\"0700000007\",\"contenu\":\"Test\"}")
                .andExpect(jsonPath("$.message", containsString("Aucun numéro WhatsApp")));
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
