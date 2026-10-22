package ci.ubipharm.crm;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Ma journee Marketing (remarque 10 du 22/10/2026) : promotions qui commencent ou finissent, evenements a venir, reponses du jour. */
class MaJourneeMarketingIT extends IntegrationBase {

    @Test
    void laJourneeMontreCeQuiCommenceOuFinitDansLHorizon() throws Exception {
        reinitialiser();
        MockHttpSession admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        LocalDate j = LocalDate.now(ZoneOffset.UTC);
        promotion("COMMENCE", j.plusDays(2), j.plusDays(20), "PROGRAMMEE");
        promotion("FINIT", j.minusDays(10), j.plusDays(3), "ACTIVE");
        promotion("LOIN", j.plusDays(30), j.plusDays(40), "PROGRAMMEE");
        promotion("BROUILLON", j.plusDays(1), j.plusDays(5), "BROUILLON");
        jdbc.update("insert into crm_evenement (nom, nature, date_debut, annuel, cree_par, created_at) values ('Fête test', 'FETE', ?, false, 1, utc_timestamp())",
                j.plusDays(4));
        jdbc.update("insert into crm_evenement (nom, nature, date_debut, annuel, cree_par, created_at) values ('Fête lointaine', 'FETE', ?, false, 1, utc_timestamp())",
                j.plusDays(40));

        mvc.perform(get("/api/marketing/ma-journee").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.horizonJours").value(7))
                .andExpect(jsonPath("$.nbReponsesDuJour").value(0))
                .andExpect(jsonPath("$.promotions.length()").value(2))
                .andExpect(jsonPath("$.promotions[?(@.nom == 'COMMENCE')].moment").value(hasItem("COMMENCE")))
                .andExpect(jsonPath("$.promotions[?(@.nom == 'FINIT')].moment").value(hasItem("FINIT")))
                .andExpect(jsonPath("$.evenements[*].nom").value(hasItem("Fête test")))
                .andExpect(jsonPath("$.evenements[*].nom").value(not(hasItem("Fête lointaine"))));

        jdbc.update("update crm_parametre set valeur = '45' where cle = 'marketing.journee_horizon_jours'");
        mvc.perform(get("/api/marketing/ma-journee").session(admin)).andExpect(jsonPath("$.promotions.length()").value(3))
                .andExpect(jsonPath("$.evenements[*].nom").value(hasItem("Fête lointaine")));

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/marketing/ma-journee").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    private void promotion(String nom, LocalDate debut, LocalDate fin, String statut) {
        jdbc.update("insert into crm_promotion (code, nom, date_debut, date_fin, statut, cree_par, created_at) values (?, ?, ?, ?, ?, 1, utc_timestamp())",
                nom, nom, debut, fin, statut);
    }
}
