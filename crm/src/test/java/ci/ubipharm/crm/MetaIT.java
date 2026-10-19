package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleMetaService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** API WhatsApp Cloud de Meta, en option par numero : passage du numero, modeles approuves, fenetre de 24 h, webhook. Meta est simule. */
class MetaIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final String NUMERO_AGENT = "2250711111111";
    private static final String NUMERO_CLIENT = "2250501010101";
    private static final String PHONE_ID = "109876543210";
    private static final String SECRET = "secret-application";

    @MockitoBean
    PasserelleMetaService meta;

    @MockitoBean
    PasserelleWhatsappService waWeb;

    @Autowired
    EnvoiWhatsappService envoiWhatsappService;

    private MockHttpSession admin;
    private Long agent;
    private Long modele;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        agent = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent1'", Long.class);
        jdbc.update("update crm_utilisateur set numero_whatsapp = ? where id = ?", NUMERO_AGENT, agent);
        for (String[] p : new String[][] {{"whatsapp.envoi.heure_debut", "0"}, {"whatsapp.envoi.heure_fin", "24"},
                {"whatsapp.envoi.delai_min_secondes", "0"}, {"whatsapp.envoi.delai_max_secondes", "0"}, {"whatsapp.envoi.plafond_jour", "200"}}) {
            jdbc.update("update crm_parametre set valeur = ? where cle = ?", p[1], p[0]);
        }
        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, agence_id, created_at) "
                + "values ('M-C1', 'Pharmacie M-C1', 'Dr KOUASSI', ?, utc_timestamp())", YOPOUGON);
        Long c1 = jdbc.queryForObject("select id from crm_client where code = 'M-C1'", Long.class);
        jdbc.update("insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, created_at) "
                + "values (?, 'Dr KOUASSI', 'PRINCIPAL', ?, 1, utc_timestamp())", c1, NUMERO_CLIENT);
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c1, agent, agent);
        LocalDate echeance = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                values ('M-F1', ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, 50000, 50000, utc_timestamp())
                """, c1, echeance.minusDays(30), echeance);
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        modele = idDe(envoyer(admin, post("/api/recouvrement/modeles-messages"),
                "{\"nom\":\"Retard\",\"typeCampagne\":\"APRES_ECHEANCE\",\"contenu\":\"Bonjour {nom_client},\\nfacture {reference_facture} : {nom_client}\"}"));
    }

    @Test
    void lAdministrateurPasseUnNumeroALApiOfficielle() throws Exception {
        envoyer(admin, put("/api/whatsapp/meta"), "{\"jetonAcces\":\"EAAG-secret\",\"secretApplication\":\"" + SECRET + "\",\"versionApi\":\"v21.0\"}")
                .andExpect(jsonPath("$.jetonRenseigne").value(true))
                .andExpect(jsonPath("$.secretRenseigne").value(true))
                .andExpect(jsonPath("$.jetonAcces").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where details like '%EAAG%'", Long.class)).isZero();
        // Un champ vide garde le jeton enregistre
        envoyer(admin, put("/api/whatsapp/meta"), "{\"jetonAcces\":\"\",\"versionApi\":\"v22.0\"}").andExpect(jsonPath("$.jetonRenseigne").value(true));

        when(meta.verifierNumero(PHONE_ID)).thenReturn("+225 07 11 11 11 11 · UBIPHARM CI");
        MockHttpSession agentSession = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
        envoyer(agentSession, post("/api/whatsapp/sessions/" + agent + "/meta"), "{\"phoneNumberId\":\"" + PHONE_ID + "\"}").andExpect(status().isForbidden());
        envoyer(admin, post("/api/whatsapp/sessions/" + agent + "/meta"), "{\"phoneNumberId\":\"" + PHONE_ID + "\"}")
                .andExpect(jsonPath("$.fournisseur").value("META"))
                .andExpect(jsonPath("$.statut").value("CONNECTE"))
                .andExpect(jsonPath("$.raison").value("+225 07 11 11 11 11 · UBIPHARM CI"));
        verify(waWeb).deconnecter(NUMERO_AGENT);
        // Un numero de l'API n'a pas de QR : « connecter » le verifie chez Meta
        mvc.perform(post("/api/whatsapp/sessions/" + agent + "/connexion").session(agentSession).with(csrf())).andExpect(jsonPath("$.statut").value("CONNECTE"));
        verify(waWeb, never()).demarrer(anyString());

        envoyer(admin, post("/api/whatsapp/sessions/" + agent + "/whatsapp-web"), "")
                .andExpect(jsonPath("$.fournisseur").value("WA_WEB")).andExpect(jsonPath("$.statut").value("DECONNECTE"));
    }

    @Test
    void horsDeLaFenetreDe24hSeulUnModeleApprouvePart() throws Exception {
        passerMeta();
        lancerCampagne();
        // Sans modele Meta, le message echoue avec la consigne
        envoiWhatsappService.envoyerLot();
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where statut = 'ECHEC'", String.class))
                .isEqualTo("Hors des 24 h qui suivent une réponse du client, Meta n'accepte qu'un modèle approuvé : renseignez le modèle Meta de « Retard »");

        envoyer(admin, put("/api/recouvrement/modeles-messages/" + modele), """
                {"nom":"Retard","typeCampagne":"APRES_ECHEANCE","contenu":"Bonjour {nom_client},\\nfacture {reference_facture} : {nom_client}",
                 "metaModele":"relance_retard","metaLangue":"fr"}""").andExpect(jsonPath("$.metaModele").value("relance_retard"));
        lancerCampagne();
        when(meta.envoyerModele(eq(PHONE_ID), eq(NUMERO_CLIENT), eq("relance_retard"), eq("fr"), any(), isNull()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("wamid.1").build());
        envoiWhatsappService.envoyerLot();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> parametres = ArgumentCaptor.forClass(List.class);
        verify(meta).envoyerModele(eq(PHONE_ID), eq(NUMERO_CLIENT), eq("relance_retard"), eq("fr"), parametres.capture(), isNull());
        // {{1}} = nom_client, {{2}} = reference_facture : une variable citee deux fois ne compte qu'une fois
        assertThat(parametres.getValue()).containsExactly("Dr KOUASSI", "M-F1");
        verify(meta, never()).envoyerTexte(anyString(), anyString(), anyString());

        // Le client repond : la fenetre de 24 h s'ouvre, la relance suivante part en texte libre
        notifier("""
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"field":"messages","value":{
                 "messaging_product":"whatsapp","metadata":{"display_phone_number":"2250711111111","phone_number_id":"%s"},
                 "contacts":[{"profile":{"name":"Dr K"},"wa_id":"%s"}],
                 "messages":[{"from":"%s","id":"wamid.R1","timestamp":"1","type":"text","text":{"body":"Je paie demain"}}]}}]}]}
                """.formatted(PHONE_ID, NUMERO_CLIENT, NUMERO_CLIENT)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select contenu from crm_message_recu where identifiant_whatsapp = 'wamid.R1'", String.class)).isEqualTo("Je paie demain");
        lancerCampagne();
        when(meta.envoyerTexte(eq(PHONE_ID), eq(NUMERO_CLIENT), anyString())).thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("wamid.2").build());
        envoiWhatsappService.envoyerLot();
        verify(meta).envoyerTexte(PHONE_ID, NUMERO_CLIENT, "Bonjour Dr KOUASSI,\nfacture M-F1 : Dr KOUASSI");
    }

    @Test
    void leWebhookExigeLeJetonPuisLaSignatureEtSuitLesAccuses() throws Exception {
        String jeton = jdbc.queryForObject("select jeton_verification from crm_compte_meta where id = 1", String.class);
        mvc.perform(get("/api/passerelle/meta/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "faux").param("hub.challenge", "42"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/passerelle/meta/webhook").param("hub.mode", "subscribe").param("hub.verify_token", jeton).param("hub.challenge", "42"))
                .andExpect(status().isOk()).andExpect(content().string("42"));

        passerMeta();
        String statut = """
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"%s"},"statuses":[{"id":"wamid.1","status":"%s"%s}]}}]}]}""";
        mvc.perform(post("/api/passerelle/meta/webhook").contentType(MediaType.APPLICATION_JSON).header("X-Hub-Signature-256", "sha256=00")
                .content(statut.formatted(PHONE_ID, "delivered", ""))).andExpect(status().isUnauthorized());

        envoyer(admin, put("/api/recouvrement/modeles-messages/" + modele), """
                {"nom":"Retard","typeCampagne":"APRES_ECHEANCE","contenu":"Bonjour {nom_client}","metaModele":"relance_retard"}""");
        lancerCampagne();
        when(meta.envoyerModele(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("wamid.1").build());
        envoiWhatsappService.envoyerLot();
        notifier(statut.formatted(PHONE_ID, "delivered", "")).andExpect(status().isOk());
        assertThat(statutMessage()).isEqualTo("DISTRIBUE");
        notifier(statut.formatted(PHONE_ID, "read", "")).andExpect(status().isOk());
        assertThat(statutMessage()).isEqualTo("LU");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'M-F1'", String.class)).isEqualTo("MESSAGE_LU");

        // Un autre envoi refuse apres coup par Meta passe en echec
        lancerCampagne();
        when(meta.envoyerModele(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("wamid.3").build());
        envoiWhatsappService.envoyerLot();
        notifier("""
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"%s"},"statuses":[{"id":"wamid.3","status":"failed",
                 "errors":[{"code":131026,"title":"Message undeliverable","error_data":{"details":"Numéro sans WhatsApp"}}]}]}}]}]}"""
                .formatted(PHONE_ID)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where identifiant_whatsapp = 'wamid.3'", String.class))
                .isEqualTo("Refusé par Meta : Numéro sans WhatsApp");
    }

    private void passerMeta() throws Exception {
        envoyer(admin, put("/api/whatsapp/meta"), "{\"jetonAcces\":\"EAAG-secret\",\"secretApplication\":\"" + SECRET + "\",\"versionApi\":\"v21.0\"}");
        when(meta.verifierNumero(PHONE_ID)).thenReturn("+225 07 11 11 11 11");
        envoyer(admin, post("/api/whatsapp/sessions/" + agent + "/meta"), "{\"phoneNumberId\":\"" + PHONE_ID + "\"}").andExpect(status().isOk());
    }

    /** Nouvelle campagne sur la facture (la precedente est close). */
    private void lancerCampagne() throws Exception {
        jdbc.update("update crm_campagne set statut = 'TERMINEE' where statut = 'EN_COURS'");
        Long campagne = idDe(envoyer(admin, post("/api/recouvrement/campagnes"),
                "{\"nom\":\"Retards " + System.nanoTime() + "\",\"typeCampagne\":\"APRES_ECHEANCE\",\"modeleId\":" + modele + ",\"criteres\":{}}"));
        envoyer(admin, post("/api/recouvrement/campagnes/" + campagne + "/soumission"), "").andExpect(status().isOk());
        envoyer(admin, post("/api/recouvrement/campagnes/" + campagne + "/validation"), "").andExpect(jsonPath("$.statut").value("EN_COURS"));
    }

    private String statutMessage() {
        return jdbc.queryForObject("select statut from crm_campagne_message where identifiant_whatsapp = 'wamid.1'", String.class);
    }

    private ResultActions notifier(String corps) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(post("/api/passerelle/meta/webhook").contentType(MediaType.APPLICATION_JSON).header("X-Hub-Signature-256", signature)
                .content(corps));
    }

    private ResultActions envoyer(MockHttpSession session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
                                  String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
