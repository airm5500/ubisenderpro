package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Factures saisies a la main : memes regles que l'import (reference unique, solde, avoir en negatif). */
class FactureIT extends IntegrationBase {

    private MockHttpSession admin;
    private Long clientId;
    private Long sousTypeId;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        String client = enregistrer(admin, post("/api/clients"), """
                {"code":"C-8001","raisonSociale":"Pharmacie Test","agenceId":1}
                """).andReturn().getResponse().getContentAsString();
        clientId = ((Number) JsonPath.read(client, "$.id")).longValue();
        sousTypeId = jdbc.queryForObject("select min(id) from crm_sous_type_compte where statut = 'ACTIF'", Long.class);
    }

    @Test
    void uneFactureSaisieSeRelanceEtUnSoldeAZeroLaSolde() throws Exception {
        String reponse = enregistrer(admin, post("/api/recouvrement/factures"), facture("F-1001", "FACTURE", "2026-10-01", "2026-10-31", 150000, 150000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("A_RELANCER"))
                .andExpect(jsonPath("$.clientCode").value("C-8001"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(reponse, "$.id")).longValue();

        enregistrer(admin, post("/api/recouvrement/factures"), facture("f-1001", "FACTURE", "2026-10-01", "2026-10-31", 10, 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La facture f-1001 existe déjà."));
        enregistrer(admin, post("/api/recouvrement/factures"), facture("F-1002", "FACTURE", "2026-10-31", "2026-10-01", 10, 20))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("précède la date de facture"),
                        org.hamcrest.Matchers.containsString("dépasse le montant"))));

        enregistrer(admin, put("/api/recouvrement/factures/" + id), facture(null, "FACTURE", "2026-10-01", "2026-10-31", 150000, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOLDEE"))
                .andExpect(jsonPath("$.reference").value("F-1001"));
        assertThat(jdbc.queryForObject("select details from crm_journal_action where action = 'FACTURE_MODIFICATION'", String.class))
                .contains("Solde : 150000.00 -> 0.00");
    }

    @Test
    void unAvoirEstEnregistreEnNegatif() throws Exception {
        enregistrer(admin, post("/api/recouvrement/factures"), facture("AV-1", "AVOIR", "2026-10-01", "2026-10-01", 5000, 5000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nature").value("AVOIR"))
                .andExpect(jsonPath("$.montant").value(-5000.0))
                .andExpect(jsonPath("$.solde").value(-5000.0));
    }

    @Test
    void unAgentNeSaisitPasDeFacture() throws Exception {
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        enregistrer(agent, post("/api/recouvrement/factures"), facture("F-2001", "FACTURE", "2026-10-01", "2026-10-31", 10, 10))
                .andExpect(status().isForbidden());
    }

    private String facture(String reference, String nature, String date, String echeance, long montant, long solde) {
        return """
                {"reference":%s,"clientId":%d,"sousTypeId":%d,"nature":"%s","dateFacture":"%s","dateEcheance":"%s","montant":%d,"solde":%d}
                """.formatted(reference == null ? "null" : "\"" + reference + "\"", clientId, sousTypeId, nature, date, echeance, montant, solde);
    }

    private ResultActions enregistrer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
