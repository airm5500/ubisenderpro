package ci.ubipharm.crm;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Authentification, CSRF, verrouillage et cloisonnement par agence. */
class SecuriteEtCloisonnementIT extends IntegrationBase {

    @BeforeEach
    void donnees() {
        reinitialiser();
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH1', 'E2E Pharmacie Yopougon', 1, utc_timestamp())");
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH2', 'E2E Pharmacie Vridi', 2, utc_timestamp())");

        creerUtilisateur("e2e-agent", "AGENT", "RECOUVREMENT", 1L);
    }

    @Test
    void unAppelAnonymeEstRefuse() throws Exception {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void laConnexionExigeLeJetonCsrf() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON).content(identifiants("admin", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAdministrateurVoitToutesLesAgences() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void lAgentNeVoitQueSonPortefeuille() throws Exception {
        // Deux pharmacies de Yopougon : seule la premiere est dans le portefeuille de l'agent (decision du 22/10/2026)
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values ('E2E-PH3', 'E2E Pharmacie Yopougon 2', 1, utc_timestamp())");
        jdbc.update("""
                insert into crm_affectation (client_id, agent_id, date_debut, affecte_par)
                select c.id, u.id, utc_timestamp(), (select id from crm_utilisateur where login = 'admin')
                from crm_client c, crm_utilisateur u where c.code = 'E2E-PH1' and u.login = 'e2e-agent'
                """);
        MockHttpSession session = connecter("e2e-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements", hasSize(1)))
                .andExpect(jsonPath("$.elements[*].code", contains("E2E-PH1")));

        Long clientVridi = jdbc.queryForObject("select id from crm_client where code = 'E2E-PH2'", Long.class);
        Long horsPortefeuille = jdbc.queryForObject("select id from crm_client where code = 'E2E-PH3'", Long.class);
        mvc.perform(get("/api/clients/" + clientVridi).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clients/" + horsPortefeuille).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/agences").session(session)).andExpect(jsonPath("$", hasSize(1)));
        // L'en-tete du Marketing ne lui ouvre rien : il n'est pas habilite sur ce module
        mvc.perform(get("/api/clients").header("X-Module", "MARKETING").session(session)).andExpect(jsonPath("$.elements", hasSize(1)));
    }

    @Test
    void leMarketingVoitLesClientsDeToutesLesAgences() throws Exception {
        creerUtilisateur("e2e-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        MockHttpSession marketing = connecter("e2e-marketing", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").header("X-Module", "MARKETING").session(marketing)).andExpect(jsonPath("$.total").value(2));
        // Sans le module Recouvrement, tout ecran commun est vu depuis le Marketing
        mvc.perform(get("/api/clients").session(marketing)).andExpect(jsonPath("$.total").value(2));

        // Habilite sur les deux modules : toutes les agences en Marketing, son seul portefeuille en Recouvrement
        jdbc.update("""
                insert into crm_habilitation (utilisateur_id, role_id, module_id, created_at)
                select u.id, r.id, m.id, utc_timestamp() from crm_utilisateur u, crm_role r, crm_module m
                where u.login = 'e2e-agent' and r.code = 'CHARGE_MARKETING' and m.code = 'MARKETING'
                """);
        MockHttpSession double_ = connecter("e2e-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").header("X-Module", "MARKETING").session(double_)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/clients").header("X-Module", "RECOUVREMENT").session(double_)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/recouvrement/factures").header("X-Module", "MARKETING").session(double_)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void leDgEtLeDirecteurCommercialSontEnConsultation() {
        for (String role : new String[] { "DG", "DIR_COMMERCIAL" }) {
            Integer ecriture = jdbc.queryForObject("""
                    select count(*) from crm_role_droit rd join crm_role r on r.id = rd.role_id join crm_droit d on d.id = rd.droit_id
                    where r.code = ? and rd.actif = true and (d.code like '%_GERER' or d.code like '%_VALIDER' or d.code like '%_PREPARER'
                      or d.code in ('AGENCES', 'REFERENTIELS', 'IMPORTS', 'UTILISATEURS', 'ROLES', 'PARAMETRES'))
                    """, Integer.class, role);
            org.assertj.core.api.Assertions.assertThat(ecriture).as(role).isZero();
            Integer lecture = jdbc.queryForObject("""
                    select count(*) from crm_role_droit rd join crm_role r on r.id = rd.role_id join crm_droit d on d.id = rd.droit_id
                    where r.code = ? and rd.actif = true and d.code in ('CLIENTS', 'TABLEAU_BORD', 'CAMPAGNES_MARKETING')
                    """, Integer.class, role);
            org.assertj.core.api.Assertions.assertThat(lecture).as(role).isEqualTo(3);
        }
    }

    @Test
    void leCompteEstVerrouilleApresCinqEchecs() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(identifiants("e2e-agent", "mauvais")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect."));
        }
        mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(identifiants("e2e-agent", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isUnauthorized());
        String statut = jdbc.queryForObject("select statut from crm_utilisateur where login = 'e2e-agent'", String.class);
        org.assertj.core.api.Assertions.assertThat(statut).isEqualTo("DESACTIVE");
    }

    @Test
    void uneTailleDePageTropGrandeEstRefuseeAvecLeChampEnCause() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/clients").param("taille", "500").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champs[0].champ").value("taille"));
    }
}
