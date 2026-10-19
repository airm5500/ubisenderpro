package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Listes de diffusion du Marketing : criteres, ajouts et exclusions a la main, desabonnement par STOP. */
class ListeDiffusionIT extends IntegrationBase {

    private MockHttpSession admin;
    private Long platine;
    private Long sansNumero;
    private Long autreAgence;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        platine = client(admin, """
                {"code":"M-1","raisonSociale":"Pharmacie Platine","agenceId":1,"segmentation":"P","villeId":%d,"numeros":{"PRINCIPAL":"0700000001"}}""".formatted(referentiel("VILLE", "Abidjan")));
        sansNumero = client(admin, """
                {"code":"M-2","raisonSociale":"Pharmacie Sans Numero","agenceId":1,"segmentation":"S"}""");
        autreAgence = client(admin, """
                {"code":"M-3","raisonSociale":"Pharmacie Vridi","agenceId":2,"segmentation":"P","email":"vridi@exemple.ci"}""");
    }

    @Test
    void uneListeSuitSesCriteresSesAjoutsEtSesExclusions() throws Exception {
        mvc.perform(post("/api/marketing/listes/simulation").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parCriteres\":true,\"segments\":[\"P\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbClients").value(2));
        Long liste = liste(admin, "{\"nom\":\"Platinum\",\"parCriteres\":true,\"segments\":[\"P\"]}");

        clients(admin, liste, "AJOUT", sansNumero).andExpect(jsonPath("$.nbAjouts").value(1)).andExpect(jsonPath("$.nbClients").value(3));
        clients(admin, liste, "EXCLUSION", autreAgence).andExpect(jsonPath("$.nbExclusions").value(1)).andExpect(jsonPath("$.nbClients").value(2));

        mvc.perform(get("/api/marketing/listes/" + liste + "/membres").session(admin))
                .andExpect(jsonPath("$.nbClients").value(2))
                .andExpect(jsonPath("$.nbJoignables").value(1))
                .andExpect(jsonPath("$.nbSansCoordonnees").value(1))
                .andExpect(jsonPath("$.membres.elements[0].code").value("M-1"))
                .andExpect(jsonPath("$.membres.elements[0].origine").value("CRITERES"))
                .andExpect(jsonPath("$.membres.elements[0].numeros[0]").value("2250700000001"))
                .andExpect(jsonPath("$.membres.elements[1].origine").value("AJOUT"))
                .andExpect(jsonPath("$.membres.elements[1].motifExclusion").value("SANS_COORDONNEES"));
        mvc.perform(get("/api/marketing/listes/" + liste + "/membres").param("filtre", "EXCLUS").session(admin))
                .andExpect(jsonPath("$.membres.total").value(1))
                .andExpect(jsonPath("$.membres.elements[0].code").value("M-3"));

        // Retirer l'exclusion remet le client que les criteres retiennent
        clients(admin, liste, "RETRAIT", autreAgence).andExpect(jsonPath("$.nbExclusions").value(0)).andExpect(jsonPath("$.nbClients").value(3));
        assertThat(jdbc.queryForObject("select count(*) from crm_liste_diffusion_client where liste_id = ?", Integer.class, liste)).isEqualTo(2);

        mvc.perform(post("/api/marketing/listes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"platinum\",\"parCriteres\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Une liste « platinum » existe déjà."));
    }

    @Test
    void stopDesabonneDuMarketingSeulementEtSeReabonneALaMain() throws Exception {
        Long liste = liste(admin, "{\"nom\":\"Tous\",\"parCriteres\":true}");
        mvc.perform(post("/api/passerelle/wa-web/message").header("X-Api-Token", "jeton-de-test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"2250700009999\",\"from\":\"2250700000001\",\"text\":\" Stop ! \",\"id\":\"IN-STOP\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select desabonne_marketing_motif from crm_client where id = ?", String.class, platine))
                .isEqualTo("Réponse WhatsApp « Stop ! »");
        mvc.perform(get("/api/marketing/listes/" + liste + "/membres").param("filtre", "NON_JOIGNABLES").session(admin))
                .andExpect(jsonPath("$.nbDesabonnes").value(1))
                .andExpect(jsonPath("$.membres.elements[?(@.code == 'M-1')].motifExclusion").value(org.hamcrest.Matchers.contains("DESABONNE")));

        mvc.perform(post("/api/marketing/clients/" + platine + "/reabonnement").session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/marketing/listes/" + liste).session(admin)).andExpect(jsonPath("$.nbJoignables").value(2));
    }

    @Test
    void leChargeMarketingNeVoitQueSonAgenceEtLAgentPasLesListes() throws Exception {
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        MockHttpSession charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);
        Long liste = liste(charge, "{\"nom\":\"Mon agence\",\"parCriteres\":true}");
        mvc.perform(get("/api/marketing/listes/" + liste).session(charge)).andExpect(jsonPath("$.nbClients").value(2));
        clients(charge, liste, "AJOUT", autreAgence).andExpect(status().isNotFound());

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/marketing/listes").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    private Long client(MockHttpSession session, String json) throws Exception {
        String r = envoyer(session, post("/api/clients"), json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private Long liste(MockHttpSession session, String json) throws Exception {
        String r = envoyer(session, post("/api/marketing/listes"), json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private ResultActions clients(MockHttpSession session, Long liste, String sens, Long client) throws Exception {
        return envoyer(session, post("/api/marketing/listes/" + liste + "/clients"), "{\"sens\":\"" + sens + "\",\"clientIds\":[" + client + "]}");
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
