package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Catalogue des produits du Marketing : saisie a la main, archivage, import par l'assistant sans le droit Imports. */
class CatalogueIT extends IntegrationBase {

    private MockHttpSession admin;
    private MockHttpSession charge;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void unProduitSeSaisitSeModifieEtSArchiveSansJamaisEtreSupprime() throws Exception {
        Long doliprane = idDe(envoyer(charge, post("/api/marketing/produits"), """
                {"code":"DOL500","libelle":"Doliprane 500 mg","categorie":"Antalgiques","laboratoire":"Sanofi","prix":1250}"""));
        envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"VIT-C\",\"libelle\":\"Vitamine C\",\"categorie\":\"Vitamines\"}")
                .andExpect(status().isOk());
        envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"dol500\",\"libelle\":\"Autre\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le code dol500 est déjà utilisé par « Doliprane 500 mg »."));
        envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"X\",\"libelle\":\"Prix faux\",\"prix\":-1}")
                .andExpect(status().isBadRequest());

        envoyer(charge, put("/api/marketing/produits/" + doliprane), """
                {"code":"DOL500","libelle":"Doliprane 500 mg comprimés","categorie":"Antalgiques","laboratoire":"Sanofi","prix":1300}""")
                .andExpect(jsonPath("$.prix").value(1300))
                .andExpect(jsonPath("$.modifiePar").value("Test it-marketing"));
        mvc.perform(get("/api/marketing/produits").param("q", "doli").session(charge))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].libelle").value("Doliprane 500 mg comprimés"));
        mvc.perform(get("/api/marketing/produits/filtres").session(charge))
                .andExpect(jsonPath("$.categories", contains("Antalgiques", "Vitamines")))
                .andExpect(jsonPath("$.laboratoires", contains("Sanofi")));

        envoyer(charge, post("/api/marketing/produits/" + doliprane + "/archivage"), "").andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(get("/api/marketing/produits").session(charge)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/marketing/produits").param("statut", "TOUS").session(charge)).andExpect(jsonPath("$.total").value(2));
        envoyer(charge, put("/api/marketing/produits/" + doliprane), "{\"code\":\"DOL500\",\"libelle\":\"Retouche\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce produit est archivé : réactivez-le pour le modifier."));
        envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"DOL500\",\"libelle\":\"Ressaisi\"}")
                .andExpect(jsonPath("$.message").value("Le code DOL500 est déjà utilisé par « Doliprane 500 mg comprimés », produit archivé : réactivez-le."));
        envoyer(charge, post("/api/marketing/produits/" + doliprane + "/reactivation"), "").andExpect(jsonPath("$.statut").value("ACTIF"));
        assertThat(jdbc.queryForList("select action from crm_journal_action where entite = 'crm_produit' order by id", String.class))
                .containsExactly("PRODUIT_CREATION", "PRODUIT_CREATION", "PRODUIT_MODIFICATION", "PRODUIT_ARCHIVAGE", "PRODUIT_REACTIVATION");

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/marketing/produits").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void leChargeImporteLeCatalogueParLAssistantSansToucherAuxAutresImports() throws Exception {
        idDe(envoyer(charge, post("/api/marketing/produits"), """
                {"code":"DOL500","libelle":"Doliprane 500 mg","laboratoire":"Sanofi","prix":1250,"description":"Fiche saisie"}"""));
        idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"VIT-C\",\"libelle\":\"Vitamine C\"}"));
        Long archive = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"OLD\",\"libelle\":\"Ancien\"}"));
        envoyer(charge, post("/api/marketing/produits/" + archive + "/archivage"), "").andExpect(status().isOk());

        String csv = """
                Désignation;Code article;Famille;Labo;PP;Observation
                Doliprane 500 mg;DOL500;Antalgiques;Sanofi;1 300;
                Vitamine C;VIT-C;;;;
                Amoxicilline 1 g;AMOX1;Antibiotiques;Sandoz;2500;Sur ordonnance
                Sans code;;;;;
                Prix faux;ERR1;;;abc;
                Amoxicilline bis;AMOX1;;;;
                Ancien;OLD;;;;
                """;
        mvc.perform(multipart("/api/imports/produits/apercu").file(fichier(csv)).session(charge).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correspondance.code_produit").value("code_article"))
                .andExpect(jsonPath("$.correspondance.libelle").value("designation"))
                .andExpect(jsonPath("$.correspondance.prix").value("pp"));
        String simulation = mvc.perform(multipart("/api/marketing/imports/produits").file(fichier(csv)).session(charge).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRODUITS"))
                .andExpect(jsonPath("$.nbCreations").value(1))
                .andExpect(jsonPath("$.nbMisesAJour").value(1))
                .andExpect(jsonPath("$.nbIdentiques").value(1))
                .andExpect(jsonPath("$.nbRejets").value(4))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(simulation, "$.id")).longValue();
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(charge))
                .andExpect(jsonPath("$.elements[*].message", contains("Code produit manquant.", "Prix illisible : abc.",
                        "Code déjà présent ligne 4 du fichier.", "Ce produit est archivé : réactivez-le dans le catalogue avant de le réimporter.")));
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "MISE_A_JOUR").session(charge))
                .andExpect(jsonPath("$.elements[0].message", org.hamcrest.Matchers.matchesPattern("Catégorie : vide -> Antalgiques ; Prix public : 1.250,00 -> 1.300,00")));

        envoyer(charge, post("/api/marketing/imports/produits/" + id + "/validation"), "{\"toutesLesMisesAJour\":true}")
                .andExpect(jsonPath("$.nbCrees").value(1))
                .andExpect(jsonPath("$.nbMisAJour").value(1));
        assertThat(jdbc.queryForMap("select categorie, prix, description, import_id from crm_produit where code = 'DOL500'"))
                .containsEntry("categorie", "Antalgiques")
                .containsEntry("prix", new java.math.BigDecimal("1300.00"))
                .containsEntry("description", "Fiche saisie")
                .containsEntry("import_id", id);
        mvc.perform(get("/api/marketing/produits").param("q", "AMOX1").session(charge))
                .andExpect(jsonPath("$.elements[0].importe").value(true))
                .andExpect(jsonPath("$.elements[0].laboratoire").value("Sandoz"));

        // Les imports du Recouvrement et des clients restent fermes au charge marketing
        mvc.perform(get("/api/imports/clients/champs").session(charge)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/imports/clients").file(fichier("Code;Nom\n1;A\n")).session(charge).with(csrf())).andExpect(status().isForbidden());
        String autre = mvc.perform(multipart("/api/imports/clients").file(fichier("Numéro de compte;Nom de la pharmacie;Agence\n9001;A;Y\n"))
                        .session(admin).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/imports").session(charge))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].type").value("PRODUITS"));
        mvc.perform(get("/api/imports/" + JsonPath.read(autre, "$.id")).session(charge)).andExpect(status().isNotFound());
        mvc.perform(get("/api/imports").session(admin)).andExpect(jsonPath("$.total").value(2));
    }

    private MockMultipartFile fichier(String contenu) {
        return new MockMultipartFile("fichier", "produits.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        String corps = resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.id")).longValue();
    }
}
