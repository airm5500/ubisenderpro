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
        jdbc.update("delete from crm_message_recu");
        jdbc.update("delete from crm_session_whatsapp");
        jdbc.update("update crm_compte_meta set jeton_acces = null, secret_application = null, version_api = 'v21.0', modifie_par = null");
        jdbc.update("delete from crm_campagne_message");
        jdbc.update("delete from crm_fournisseur_sms where code <> 'LETEXTO'");
        jdbc.update("update crm_fournisseur_sms set par_defaut = true, actif = true, cle_api = null, modifie_par = null where code = 'LETEXTO'");
        jdbc.update("delete from crm_campagne");
        jdbc.update("delete from crm_evenement where cree_par is not null");
        jdbc.update("update crm_evenement set statut = 'ACTIF', modifie_par = null, updated_at = null");
        jdbc.update("delete from crm_delai_relance");
        jdbc.update("update crm_parametre set valeur = '0' where cle = 'campagne.relance_intervalle_jours'");
        jdbc.update("update crm_parametre set valeur = '1' where cle = 'campagne.apres_echeance_jours_min'");
        jdbc.update("delete from crm_piece_jointe");
        jdbc.update("delete from crm_modele_message");
        jdbc.update("delete from crm_variable_message where code not in ('code_client', 'numero_compte', 'nom_client', 'nom_pharmacie', "
                + "'agence', 'sous_type', 'reference_facture', 'date_facture', 'date_echeance', 'montant', 'solde', 'jours_retard', "
                + "'jours_avant_echeance', 'agent_nom', 'agent_numero', 'expediteur_nom', 'expediteur_numero', 'segment', "
                + "'promotion_nom', 'promotion_code', 'promotion_debut', 'promotion_fin', 'produits_promotion', 'produits_disponibles', "
                + "'evenement_nom', 'evenement_date', 'evenement_fin')");
        jdbc.update("update crm_variable_message set statut = 'ACTIF'");
        jdbc.update("delete from crm_delegation");
        jdbc.update("delete from crm_affectation");
        jdbc.update("delete from crm_facture_suivi");
        jdbc.update("delete from crm_facture_solde");
        jdbc.update("delete from crm_facture");
        jdbc.update("delete from crm_promotion_produit");
        jdbc.update("delete from crm_promotion");
        jdbc.update("delete from crm_produit");
        jdbc.update("delete from crm_import_ligne");
        jdbc.update("delete from crm_import");
        jdbc.update("delete from crm_import_modele");
        jdbc.update("delete from crm_journal_action");
        jdbc.update("delete from crm_liste_diffusion_client");
        jdbc.update("delete from crm_liste_diffusion");
        jdbc.update("delete from crm_contact");
        jdbc.update("delete from crm_client");
        jdbc.update("delete from crm_utilisateur_agence");
        jdbc.update("delete from crm_agence where code not in ('YOP', 'VRI', 'CEN', 'BKE', 'GAG')");
        jdbc.update("update crm_agence set statut = 'ACTIF', modifie_par = null, updated_at = null");
        jdbc.update("delete from crm_habilitation where utilisateur_id <> (select id from crm_utilisateur where login = 'admin')");
        jdbc.update("delete from crm_journal_connexion");
        // Roles crees par les tests ; les roles livres retrouvent leur nom et leur perimetre
        jdbc.update("delete rd from crm_role_droit rd join crm_role r on r.id = rd.role_id where r.systeme = false");
        jdbc.update("delete from crm_role where systeme = false");
        jdbc.update("update crm_role set modifie_par = null, updated_at = null, actif = true");
        jdbc.update("delete from crm_utilisateur where login <> 'admin'");
        jdbc.update("update crm_utilisateur set statut = 'ACTIF', echecs_connexion = 0, numero_whatsapp = null, mot_de_passe_provisoire = false, droits_revises_le = null where login = 'admin'");
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
