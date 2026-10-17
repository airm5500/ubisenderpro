package ci.ubipharm.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Socle des tests d'integration contre une vraie base MariaDB (ubicrm_test, recreee a chaque lancement).
 * Lancement : ./mvnw verify -Pit
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(IntegrationBase.BaseVierge.class)
abstract class IntegrationBase {

    static final String MOT_DE_PASSE_ADMIN = "Admin@2026";

    @TestConfiguration
    static class BaseVierge {
        @Bean
        FlywayMigrationStrategy nettoyerPuisMigrer() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /** Vide les donnees de test en gardant l'administrateur et les referentiels. */
    void reinitialiser() {
        jdbc.update("delete from crm_delegation");
        jdbc.update("delete from crm_affectation");
        jdbc.update("delete from crm_facture");
        jdbc.update("delete from crm_import_ligne");
        jdbc.update("delete from crm_import");
        jdbc.update("delete from crm_import_modele");
        jdbc.update("delete from crm_journal_action");
        jdbc.update("delete from crm_contact");
        jdbc.update("delete from crm_client");
        jdbc.update("delete from crm_utilisateur_agence");
        jdbc.update("delete from crm_habilitation where utilisateur_id <> (select id from crm_utilisateur where login = 'admin')");
        jdbc.update("delete from crm_journal_connexion");
        jdbc.update("delete from crm_utilisateur where login <> 'admin'");
        jdbc.update("update crm_utilisateur set statut = 'ACTIF', echecs_connexion = 0, numero_whatsapp = null where login = 'admin'");
    }

    /** Cree un utilisateur (mot de passe identique a l'admin) avec un role, un module et une agence. */
    void creerUtilisateur(String login, String role, String module, Long agenceId) {
        String hash = jdbc.queryForObject("select mot_de_passe_hash from crm_utilisateur where login = 'admin'", String.class);
        jdbc.update("insert into crm_utilisateur (login, nom_complet, mot_de_passe_hash, created_at) values (?, ?, ?, utc_timestamp())",
                login, "Test " + login, hash);
        jdbc.update("""
                insert into crm_habilitation (utilisateur_id, role_id, module_id, created_at)
                select u.id, r.id, m.id, utc_timestamp() from crm_utilisateur u, crm_role r, crm_module m
                where u.login = ? and r.code = ? and m.code = ?
                """, login, role, module);
        if (agenceId != null) {
            jdbc.update("insert into crm_utilisateur_agence (utilisateur_id, agence_id, date_debut, created_at) "
                    + "select id, ?, utc_date(), utc_timestamp() from crm_utilisateur where login = ?", agenceId, login);
        }
    }

    MockHttpSession connecter(String login, String motDePasse) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/connexion").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(identifiants(login, motDePasse)))
                .andExpect(status().isOk());
        return session;
    }

    String identifiants(String login, String motDePasse) {
        return "{\"login\":\"" + login + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }
}
