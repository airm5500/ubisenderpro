package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Journal des actions : qui a cree, modifie, desactive ou reactive quoi, retrouve par periode, auteur, type, nature et texte. */
class JournalIT extends IntegrationBase {

    private final String aujourdhui = LocalDate.now(ZoneOffset.UTC).toString();
    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void leCycleDeVieDunClientSeRetrouveDansLeJournal() throws Exception {
        Long id = creerClient("C-7001", "Pharmacie du Journal");
        envoyer(admin, put("/api/clients/" + id), """
                {"raisonSociale":"Pharmacie du Journal Neuf","agenceId":1,"numeros":{"PRINCIPAL":"0700000001"}}
                """).andExpect(status().isOk());
        envoyer(admin, post("/api/clients/" + id + "/desactivation"), "{\"motif\":\"Pharmacie fermée\"}").andExpect(status().isOk());
        envoyer(admin, post("/api/clients/" + id + "/reactivation"), "{}").andExpect(status().isOk());

        mvc.perform(get("/api/journal").session(admin).param("du", aujourdhui).param("au", aujourdhui).param("type", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                // Le plus recent d'abord
                .andExpect(jsonPath("$.elements[0].nature").value("REACTIVATION"))
                .andExpect(jsonPath("$.elements[0].natureLibelle").value("Réactivation"))
                .andExpect(jsonPath("$.elements[1].natureLibelle").value("Désactivation / archivage"))
                .andExpect(jsonPath("$.elements[2].natureLibelle").value("Modification"))
                .andExpect(jsonPath("$.elements[2].details").value(containsString("Pharmacie : Pharmacie du Journal -> Pharmacie du Journal Neuf")))
                .andExpect(jsonPath("$.elements[3].natureLibelle").value("Création"))
                .andExpect(jsonPath("$.elements[3].typeLibelle").value("Client"))
                .andExpect(jsonPath("$.elements[3].actionLibelle").value("Création"))
                .andExpect(jsonPath("$.elements[3].objet").value("C-7001 - Pharmacie du Journal Neuf"))
                .andExpect(jsonPath("$.elements[3].lien").value("/clients/" + id))
                .andExpect(jsonPath("$.elements[3].utilisateurNom").isNotEmpty())
                .andExpect(jsonPath("$.elements[3].login").value("admin"));

        mvc.perform(get("/api/journal").session(admin).param("nature", "DESACTIVATION"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].action").value("CLIENT_DESACTIVATION"))
                .andExpect(jsonPath("$.elements[0].details").value(containsString("Pharmacie fermée")));
        // Texte libre : nom du client (retrouve dans sa fiche, pas seulement dans le detail)
        mvc.perform(get("/api/journal").session(admin).param("q", "journal neuf"))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/journal").session(admin).param("q", "introuvable-xyz"))
                .andExpect(jsonPath("$.total").value(0));
        // Historique de la fiche client
        Long autre = creerClient("C-7002", "Pharmacie Voisine");
        mvc.perform(get("/api/journal").session(admin).param("clientId", String.valueOf(id)))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/journal").session(admin).param("clientId", String.valueOf(autre)))
                .andExpect(jsonPath("$.total").value(1));
        // Pagination
        mvc.perform(get("/api/journal").session(admin).param("type", "CLIENT").param("taille", "2").param("page", "1"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.elements.length()").value(2));
    }

    @Test
    void lesConnexionsEtLAuteurSontFiltrables() throws Exception {
        Long adminId = jdbc.queryForObject("select id from crm_utilisateur where login = 'admin'", Long.class);
        creerClient("C-7010", "Pharmacie Auteur");
        mvc.perform(get("/api/journal").session(admin).param("type", "SESSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].nature").value("CONNEXION"))
                .andExpect(jsonPath("$.elements[0].actionLibelle").value("Connexion réussie"))
                .andExpect(jsonPath("$.elements[0].cle").value(startsWith("C")));
        mvc.perform(get("/api/journal").session(admin).param("utilisateurId", String.valueOf(adminId)).param("nature", "CREATION"))
                .andExpect(jsonPath("$.total").value(1));
        // 0 = traitements automatiques
        mvc.perform(get("/api/journal").session(admin).param("utilisateurId", "0"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/journal").session(admin).param("type", "INCONNU")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/journal/referentiel").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types[*].libelle", hasItem("Facture")))
                .andExpect(jsonPath("$.natures[*].code", hasItem("REACTIVATION")))
                .andExpect(jsonPath("$.utilisateurs[*].libelle", hasItem("Traitements automatiques")));
    }

    @Test
    void laModificationDunUtilisateurDonneSesEcarts() throws Exception {
        creerUtilisateur("it-journal", "AGENT", "RECOUVREMENT", 1L);
        Long id = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-journal'", Long.class);
        envoyer(admin, put("/api/utilisateurs/" + id), """
                {"nomComplet":"Agent Renommé","email":"agent@exemple.ci",
                 "habilitations":[{"roleCode":"AGENT","moduleCode":"RECOUVREMENT"}],"agenceIds":[1, 2]}
                """).andExpect(status().isOk());
        String details = jdbc.queryForObject("select details from crm_journal_action where action = 'UTILISATEUR_MODIFICATION'", String.class);
        assertThat(details).startsWith("it-journal : ").contains("Nom : Test it-journal -> Agent Renommé").contains("E-mail : vide -> agent@exemple.ci")
                .contains("Agences : ");
        mvc.perform(get("/api/journal").session(admin).param("type", "UTILISATEUR"))
                .andExpect(jsonPath("$.elements[0].objet").value("Agent Renommé (it-journal)"))
                .andExpect(jsonPath("$.elements[0].lien").value("/utilisateurs"));
    }

    @Test
    void leJournalEstReserveAuDroitEtSExporte() throws Exception {
        creerClient("C-7020", "Pharmacie Export");
        creerUtilisateur("it-agent-journal", "AGENT", "RECOUVREMENT", 1L);
        creerUtilisateur("it-dg-journal", "DG", "RECOUVREMENT", null);
        mvc.perform(get("/api/journal").session(connecter("it-agent-journal", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get("/api/journal").session(connecter("it-dg-journal", MOT_DE_PASSE_ADMIN))).andExpect(status().isOk());

        byte[] csv = mvc.perform(get("/api/journal/export").session(admin).param("type", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String texte = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(texte).contains("Date;Utilisateur;Identifiant;Nature;Type de donnée").contains("C-7020 - Pharmacie Export");

        byte[] xlsx = mvc.perform(get("/api/journal/export-excel").session(admin).param("du", aujourdhui))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var classeur = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var feuille = classeur.getSheetAt(0);
            assertThat(feuille.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(feuille.getLastRowNum()).isGreaterThanOrEqualTo(1);
        }
        // L'export lui-meme est trace
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'JOURNAL_EXPORT'", Integer.class)).isEqualTo(2);
    }

    private Long creerClient(String code, String nom) throws Exception {
        String reponse = envoyer(admin, post("/api/clients"), """
                {"code":"%s","raisonSociale":"%s","agenceId":1,"numeros":{"PRINCIPAL":"0700000001"}}
                """.formatted(code, nom)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(reponse, "$.id")).longValue();
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
