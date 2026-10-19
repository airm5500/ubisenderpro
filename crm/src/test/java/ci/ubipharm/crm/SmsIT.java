package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ci.ubipharm.crm.rest.service.EnvoiSmsService;

/** Relances SMS par un fournisseur HTTP parametre en base, ici un faux LeTexto local. */
class SmsIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;

    @Autowired
    EnvoiSmsService envoiSmsService;

    /** Requete recue par le faux fournisseur. */
    record Appel(String methode, String chemin, String autorisation, String corps) {
    }

    private final List<Appel> appels = new CopyOnWriteArrayList<>();
    private volatile int statutEnvoi = 200;
    private volatile String reponseEnvoi = "{\"statut\":\"submitted\",\"id\":\"sms-1\",\"partCount\":1}";
    private volatile String reponseStatut = "{\"status\":\"DELIVERED\"}";
    private HttpServer fournisseur;

    private MockHttpSession admin;
    private Long modele;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        fournisseur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fournisseur.createContext("/v1/messages/send", e -> repondre(e, statutEnvoi, reponseEnvoi));
        fournisseur.createContext("/v1/messages/", e -> repondre(e, 200, reponseStatut));
        fournisseur.createContext("/v1/users/balance", e -> repondre(e, 200, "{\"balance\": 1520}"));
        fournisseur.start();
        String base = "http://127.0.0.1:" + fournisseur.getAddress().getPort();
        jdbc.update("update crm_fournisseur_sms set url_envoi = ?, url_statut = ?, url_solde = ?, cle_api = 'cle-test' where code = 'LETEXTO'",
                base + "/v1/messages/send", base + "/v1/messages/{identifiant}/status?token={cle}", base + "/v1/users/balance?token={cle}");
        parametre("sms.envoi.heure_debut", "0");
        parametre("sms.envoi.heure_fin", "24");
        parametre("sms.envoi.delai_secondes", "0");
        parametre("sms.envoi.pause_cle_refusee_minutes", "0");
        parametre("application.url_publique", "");

        creerUtilisateur("it-agent1", "AGENT", "RECOUVREMENT", YOPOUGON);
        Long agent1 = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-agent1'", Long.class);
        Long avecNumero = client("S-C1");
        Long sansNumero = client("S-C2");
        jdbc.update("insert into crm_contact (client_id, nom, fonction, numero_whatsapp, rang, statut, created_at) "
                + "values (?, 'Dr S-C1', 'PRINCIPAL', '2250701020304', 1, 'ACTIF', utc_timestamp())", avecNumero);
        for (Long c : new Long[] {avecNumero, sansNumero}) {
            jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) values (?, ?, utc_timestamp(), ?)", c, agent1, agent1);
        }
        facture("S-F1", avecNumero);
        facture("S-F2", sansNumero);

        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        modele = idDe(mvc.perform(post("/api/recouvrement/modeles-messages").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retard SMS\",\"typeCampagne\":\"APRES_ECHEANCE\",\"canal\":\"SMS\","
                        + "\"contenu\":\"UBIPHARM : la facture {reference_facture} reste due ({solde} FCFA). Merci.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("SMS")));
    }

    @AfterEach
    void arreter() {
        fournisseur.stop(0);
    }

    @Test
    void leSmsPartChezLeFournisseurParDefautPuisLAccuseLeDitDistribue() throws Exception {
        Long campagne = lancer();
        mvc.perform(get("/api/sms/etat").session(admin))
                .andExpect(jsonPath("$.configure").value(true))
                .andExpect(jsonPath("$.fournisseur").value("LeTexto"))
                .andExpect(jsonPath("$.expediteur").value("UBIPHARM"))
                .andExpect(jsonPath("$.enAttente").value(1));

        assertThat(envoiSmsService.envoyerSuivant()).isEqualTo(1);
        assertThat(envoiSmsService.envoyerSuivant()).isZero();

        assertThat(appels).hasSize(1);
        Appel appel = appels.getFirst();
        assertThat(appel.methode()).isEqualTo("POST");
        assertThat(appel.autorisation()).isEqualTo("Bearer cle-test");
        assertThat((String) JsonPath.read(appel.corps(), "$.from")).isEqualTo("UBIPHARM");
        assertThat((String) JsonPath.read(appel.corps(), "$.to")).isEqualTo("2250701020304");
        assertThat((String) JsonPath.read(appel.corps(), "$.content")).isEqualTo("UBIPHARM : la facture S-F1 reste due (50 000 FCFA). Merci.");
        assertThat((String) JsonPath.read(appel.corps(), "$.customData")).startsWith("CRM-");
        // Sans adresse publique du CRM, pas d'adresse d'accuse : le champ est retire plutot qu'envoye vide
        assertThat(appel.corps()).doesNotContain("dlrUrl");

        assertThat(jdbc.queryForObject("select concat(statut, ' ', identifiant_sms, ' ', nb_sms) from crm_campagne_message where campagne_id = ? "
                + "and statut <> 'EXCLU'", String.class, campagne)).isEqualTo("ENVOYE sms-1 1");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'S-F1'", String.class)).isEqualTo("RELANCE_ENVOYEE");
        assertThat(jdbc.queryForObject("select statut from crm_campagne where id = ?", String.class, campagne)).isEqualTo("TERMINEE");

        String jeton = jdbc.queryForObject("select jeton_accuse from crm_fournisseur_sms where code = 'LETEXTO'", String.class);
        mvc.perform(post("/api/passerelle/sms/LETEXTO/accuse").param("jeton", "faux").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"sms-1\",\"statuts\":\"DELIVERED\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/passerelle/sms/LETEXTO/accuse").param("jeton", jeton).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"sms-1\",\"statuts\":\"DELIVERED\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select statut from crm_campagne_message where identifiant_sms = 'sms-1'", String.class)).isEqualTo("DISTRIBUE");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'S-F1'", String.class)).isEqualTo("MESSAGE_DISTRIBUE");
    }

    @Test
    void leClientSansNumeroEstExcluEtLAdressePubliqueDonneLAccuse() throws Exception {
        parametre("application.url_publique", "https://crm.ubipharm.ci/");
        Long campagne = idDe(nouvelleCampagne());
        mvc.perform(get("/api/recouvrement/campagnes/" + campagne + "/recapitulatif").session(admin))
                .andExpect(jsonPath("$.nbMessages").value(1))
                .andExpect(jsonPath("$.exclusions[0].motif").value("Client sans numéro de téléphone"))
                .andExpect(jsonPath("$.exemples[0].numeroDestinataire").value("2250701020304"));
        valider(campagne);
        envoiSmsService.envoyerSuivant();
        String jeton = jdbc.queryForObject("select jeton_accuse from crm_fournisseur_sms where code = 'LETEXTO'", String.class);
        assertThat((String) JsonPath.read(appels.getFirst().corps(), "$.dlrUrl"))
                .isEqualTo("https://crm.ubipharm.ci/api/passerelle/sms/LETEXTO/accuse?jeton=" + jeton);
    }

    @Test
    void unNumeroRefuseEchoueEtUneCleRefuseeFaitAttendre() throws Exception {
        Long campagne = lancer();
        statutEnvoi = 401;
        reponseEnvoi = "{\"statusCode\":401,\"message\":\"Invalid username/ password\",\"error\":\"Unauthorized\"}";
        envoiSmsService.envoyerSuivant();
        assertThat(jdbc.queryForObject("select concat(statut, ' ', tentatives) from crm_campagne_message where campagne_id = ? and statut <> 'EXCLU'",
                String.class, campagne)).isEqualTo("A_ENVOYER 0");
        mvc.perform(get("/api/sms/etat").session(admin))
                .andExpect(jsonPath("$.alerte").value("Le fournisseur LeTexto refuse la clé API : les SMS attendent sa correction."));

        statutEnvoi = 400;
        reponseEnvoi = "{\"statusCode\":400,\"message\":[\"to contains invalid phone number(s): 2250701020304.\"],\"errors\":\"Bad Request\"}";
        envoiSmsService.envoyerSuivant();
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where campagne_id = ? and statut = 'ECHEC'", String.class, campagne))
                .isEqualTo("Refusé par le fournisseur : to contains invalid phone number(s): 2250701020304.");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'S-F1'", String.class)).isEqualTo("A_RELANCER");
    }

    @Test
    void sansAccuseLeStatutEstDemandeAuFournisseur() throws Exception {
        Long campagne = lancer();
        envoiSmsService.envoyerSuivant();
        jdbc.update("update crm_campagne_message set envoye_le = utc_timestamp() - interval 10 minute where campagne_id = ?", campagne);
        reponseStatut = "{\"status\":\"FAILED\"}";
        assertThat(envoiSmsService.verifierStatuts()).isEqualTo(1);
        assertThat(appels.getLast().chemin()).isEqualTo("/v1/messages/sms-1/status?token=cle-test");
        assertThat(jdbc.queryForObject("select motif from crm_campagne_message where identifiant_sms = 'sms-1'", String.class))
                .isEqualTo("Non remis par l'opérateur");
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'S-F1'", String.class)).isEqualTo("A_RELANCER");
        assertThat(jdbc.queryForObject("select nb_echecs from crm_campagne where id = ?", Integer.class, campagne)).isEqualTo(1);
    }

    @Test
    void unAutreFournisseurSeParametreSansDeveloppement() throws Exception {
        String base = "http://127.0.0.1:" + fournisseur.getAddress().getPort();
        String json = """
                {"code":"autre","libelle":"Autre opérateur","actif":true,"expediteur":"UBIPHARM","cleApi":"secret-autre","formatNumero":"PLUS",
                 "urlEnvoi":"%s/v1/messages/send?user=crm&key={cle}","methodeEnvoi":"POST","typeContenu":"application/x-www-form-urlencoded",
                 "corps":"dest={numero}&texte={message}","cheminIdentifiant":"id","cheminErreur":"message"}
                """.formatted(base);
        Long id = idDe(mvc.perform(post("/api/sms/fournisseurs").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTRE"))
                .andExpect(jsonPath("$.cleRenseignee").value(true))
                .andExpect(jsonPath("$.cleApi").doesNotExist())
                .andExpect(jsonPath("$.parDefaut").value(false)));

        mvc.perform(post("/api/sms/fournisseurs/" + id + "/essai").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"07 01 02 03 04\",\"message\":\"Essai é & ok\"}"))
                .andExpect(jsonPath("$.reussi").value(true))
                .andExpect(jsonPath("$.identifiant").value("sms-1"));
        Appel essai = appels.getLast();
        assertThat(essai.chemin()).isEqualTo("/v1/messages/send?user=crm&key=secret-autre");
        assertThat(essai.corps()).isEqualTo("dest=%2B2250701020304&texte=Essai+%C3%A9+%26+ok");

        mvc.perform(post("/api/sms/fournisseurs/" + id + "/defaut").session(admin).with(csrf()))
                .andExpect(jsonPath("$.parDefaut").value(true));
        mvc.perform(get("/api/sms/fournisseurs").session(admin))
                .andExpect(jsonPath("$[0].code").value("AUTRE"))
                .andExpect(jsonPath("$[1].parDefaut").value(false));
        // Le fournisseur par defaut ne se desactive pas ; la cle laissee vide est gardee
        mvc.perform(put("/api/sms/fournisseurs/" + id).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"actif\":true", "\"actif\":false").replace("\"cleApi\":\"secret-autre\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/sms/fournisseurs/" + id).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"cleApi\":\"secret-autre\",", "").replace("Autre opérateur", "Autre")))
                .andExpect(jsonPath("$.libelle").value("Autre"));
        assertThat(jdbc.queryForObject("select cle_api from crm_fournisseur_sms where code = 'AUTRE'", String.class)).isEqualTo("secret-autre");

        mvc.perform(get("/api/sms/fournisseurs/" + id + "/solde").session(admin)).andExpect(status().isBadRequest());
        Long letexto = jdbc.queryForObject("select id from crm_fournisseur_sms where code = 'LETEXTO'", Long.class);
        mvc.perform(get("/api/sms/fournisseurs/" + letexto + "/solde").session(admin)).andExpect(jsonPath("$.solde").value("1520"));

        MockHttpSession agent = connecter("it-agent1", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/sms/fournisseurs").session(agent)).andExpect(status().isForbidden());
    }

    private Long lancer() throws Exception {
        Long campagne = idDe(nouvelleCampagne());
        valider(campagne);
        return campagne;
    }

    private ResultActions nouvelleCampagne() throws Exception {
        return mvc.perform(post("/api/recouvrement/campagnes").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Retards SMS\",\"typeCampagne\":\"APRES_ECHEANCE\",\"modeleId\":" + modele + ",\"criteres\":{}}"))
                .andExpect(jsonPath("$.canal").value("SMS"));
    }

    private void valider(Long campagne) throws Exception {
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/soumission").session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/recouvrement/campagnes/" + campagne + "/validation").session(admin).with(csrf()))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
    }

    private void repondre(HttpExchange e, int statut, String corps) throws IOException {
        appels.add(new Appel(e.getRequestMethod(), e.getRequestURI().toString(), e.getRequestHeaders().getFirst("Authorization"),
                new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(statut, octets.length);
        e.getResponseBody().write(octets);
        e.close();
    }

    private Long client(String code) {
        jdbc.update("insert into crm_client (code, raison_sociale, nom_pharmacien, agence_id, created_at) values (?, ?, ?, ?, utc_timestamp())",
                code, "Pharmacie " + code, "Dr " + code, YOPOUGON);
        return jdbc.queryForObject("select id from crm_client where code = ?", Long.class, code);
    }

    private void facture(String reference, Long clientId) {
        LocalDate echeance = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), ?, ?, 50000, 50000, utc_timestamp())
                """, reference, clientId, echeance.minusDays(30), echeance);
    }

    private void parametre(String cle, String valeur) {
        jdbc.update("update crm_parametre set valeur = ? where cle = ?", valeur, cle);
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
