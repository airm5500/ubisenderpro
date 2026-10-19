package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;

/** Desactivation des clients, mise a jour selective et verificateur de numeros. */
class ClientCycleIT extends IntegrationBase {

    @MockitoBean
    PasserelleWhatsappService passerelle;

    private MockHttpSession admin;
    private Long c1;
    private Long c2;
    private Long c3;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        jdbc.update("delete from crm_session_whatsapp");
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        c1 = client("{\"code\":\"D-1\",\"raisonSociale\":\"Pharmacie Un\",\"agenceId\":1,\"segmentation\":\"P\",\"numeros\":{\"PRINCIPAL\":\"0700000001\",\"COMPTABLE\":\"0700000002\"}}");
        c2 = client("{\"code\":\"D-2\",\"raisonSociale\":\"Pharmacie Deux\",\"agenceId\":1,\"numeros\":{\"PRINCIPAL\":\"0700000003\"}}");
        c3 = client("{\"code\":\"D-3\",\"raisonSociale\":\"Pharmacie Trois\",\"agenceId\":2}");
    }

    @Test
    void unClientDesactiveSortDuPortefeuilleEtSeReactive() throws Exception {
        envoyer(post("/api/clients/" + c1 + "/desactivation"), "{\"motif\":\" \"}").andExpect(status().isBadRequest());
        envoyer(post("/api/clients/" + c1 + "/desactivation"), "{\"motif\":\"Pharmacie fermée\"}")
                .andExpect(jsonPath("$.statut").value("INACTIF"))
                .andExpect(jsonPath("$.desactiveMotif").value("Pharmacie fermée"))
                .andExpect(jsonPath("$.desactivePar").isNotEmpty());
        mvc.perform(get("/api/recouvrement/portefeuille").session(admin)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/recouvrement/portefeuille").param("statut", "INACTIF").session(admin))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.elements[0].code").value("D-1"));
        envoyer(put("/api/clients/" + c1), "{\"raisonSociale\":\"X\",\"agenceId\":1}")
                .andExpect(jsonPath("$.message").value("Ce client est désactivé : réactivez-le d'abord."));

        mvc.perform(post("/api/clients/" + c1 + "/reactivation").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.desactiveMotif").isEmpty());
        assertThat(jdbc.queryForObject("select details from crm_journal_action where action = 'CLIENT_REACTIVATION'", String.class))
                .contains("Pharmacie fermée");
    }

    @Test
    void laMiseAJourSelectiveNeChangeQueLesChampsChoisis() throws Exception {
        Long tournee = idDe(envoyer(post("/api/referentiels"), "{\"type\":\"TOURNEE\",\"libelle\":\"IT Nord\",\"agenceId\":1}"));
        envoyer(post("/api/clients/mise-a-jour-selective"),
                "{\"clientIds\":[%d,%d,%d],\"champs\":[\"TOURNEE\"],\"tourneeId\":%d}".formatted(c1, c2, c3, tournee))
                .andExpect(jsonPath("$.message", containsString("1 client(s) coché(s) sont d'une autre agence")));

        envoyer(post("/api/clients/mise-a-jour-selective"),
                "{\"clientIds\":[%d,%d],\"champs\":[\"SEGMENT\",\"TOURNEE\",\"LOCALISATION\"],\"segmentation\":\"s\",\"tourneeId\":%d,\"communeId\":%d}"
                        .formatted(c1, c2, tournee, referentiel("COMMUNE", "Yopougon")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbModifies").value(2));
        assertThat(jdbc.queryForList("select concat(segmentation, '/', tournee_id, '/', ville_id, '/', raison_sociale) from crm_client where id in (?, ?) order by id",
                String.class, c1, c2)).containsExactly("S/" + tournee + "/" + referentiel("VILLE", "Abidjan") + "/Pharmacie Un",
                "S/" + tournee + "/" + referentiel("VILLE", "Abidjan") + "/Pharmacie Deux");
        assertThat(jdbc.queryForObject("select details from crm_journal_action where action = 'CLIENT_MODIFICATION' and entite_id = ?", String.class, c1))
                .contains("Segment : P -> S").contains("Commune : vide -> Yopougon").contains("Tournée : vide -> IT Nord");

        // Changer d'agence fait tomber la tournee de l'ancienne
        envoyer(post("/api/clients/mise-a-jour-selective"), "{\"clientIds\":[%d],\"champs\":[\"AGENCE\"],\"agenceId\":2}".formatted(c1))
                .andExpect(jsonPath("$.nbModifies").value(1));
        assertThat(jdbc.queryForObject("select tournee_id from crm_client where id = ?", Long.class, c1)).isNull();
        envoyer(post("/api/clients/mise-a-jour-selective"), "{\"clientIds\":[%d],\"champs\":[\"RAISON_SOCIALE\"]}".formatted(c1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leVerificateurControleLeFormatPuisLaPresenceSurWhatsapp() throws Exception {
        envoyer(post("/api/clients/verification-numeros"), "{\"numeros\":[\"07 00 00 00 09\",\"123\"]}")
                .andExpect(jsonPath("$.sessionUtilisee").isEmpty())
                .andExpect(jsonPath("$.avertissement", containsString("seul le format a été contrôlé")))
                .andExpect(jsonPath("$.formatsInvalides").value(1))
                .andExpect(jsonPath("$.nonVerifies").value(1));

        jdbc.update("insert into crm_session_whatsapp (numero, statut, sante, updated_at) values ('2250500000000', 'CONNECTE', 'OK', utc_timestamp())");
        when(passerelle.verifierNumeros(eq("2250500000000"), anyList()))
                .thenReturn(Map.of("2250700000001", true, "2250700000002", false, "2250700000003", true, "2250700000009", false));
        envoyer(post("/api/clients/verification-numeros"), "{\"numeros\":[\"0700000009\"],\"clientIds\":[%d,%d]}".formatted(c1, c2))
                .andExpect(jsonPath("$.sessionUtilisee").value("2250500000000"))
                .andExpect(jsonPath("$.surWhatsapp").value(2))
                .andExpect(jsonPath("$.absents").value(2))
                .andExpect(jsonPath("$.resultats[1].client").value("Pharmacie Deux"))
                .andExpect(jsonPath("$.resultats[1].resultat").value("SUR_WHATSAPP"));
        assertThat(jdbc.queryForObject("select whatsapp_present from crm_contact where numero_whatsapp = '2250700000002'", Boolean.class)).isFalse();
    }

    private Long client(String json) throws Exception {
        return idDe(envoyer(post("/api/clients"), json).andExpect(status().isOk()));
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
