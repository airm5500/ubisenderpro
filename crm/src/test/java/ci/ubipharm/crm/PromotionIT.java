package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.PromotionService;

/** Promotions : produits et avantages, activation, fin d'office, campagne qui cite une promotion, import des produits. */
class PromotionIT extends IntegrationBase {

    private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    PromotionService promotionService;

    private MockHttpSession admin;
    private MockHttpSession charge;
    private Long doliprane;
    private Long amoxicilline;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        jdbc.update("update crm_utilisateur set numero_whatsapp = '2250722222222' where login = 'it-marketing'");
        charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);
        doliprane = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"DOL500\",\"libelle\":\"Doliprane 500 mg\",\"prix\":1250}"));
        amoxicilline = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"AMOX1\",\"libelle\":\"Amoxicilline 1 g\",\"prix\":2500}"));
        envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"VITC\",\"libelle\":\"Vitamine C\"}").andExpect(status().isOk());
    }

    @Test
    void unePromotionActiveRemplitLesMessagesDeLaCampagnePuisSeTermineDOffice() throws Exception {
        Long promo = idDe(envoyer(charge, post("/api/marketing/promotions"), promotion("NOV26", "Promo de novembre", AUJOURDHUI.plusDays(30))));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/activation"), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ajoutez au moins un produit avant d'activer la promotion."));
        Long ligneDoliprane = idDe(envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits"),
                "{\"produitId\":" + doliprane + ",\"quantiteMin\":10,\"avantage\":\"UG_QUANTITE\",\"ugQuantite\":2}"));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits"),
                "{\"produitId\":" + amoxicilline + ",\"avantage\":\"PRIX\",\"prixPromo\":2600}")
                .andExpect(jsonPath("$.message").value("Le prix promotionnel (2 600 F) n'est pas inférieur au prix public (2 500 F)."));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits"),
                "{\"produitId\":" + amoxicilline + ",\"avantage\":\"PRIX\",\"prixPromo\":2200}")
                .andExpect(jsonPath("$.resume").value("2 200 F au lieu de 2 500 F"));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits"), "{\"produitId\":" + doliprane + ",\"avantage\":\"UG_QUANTITE\",\"ugQuantite\":1}")
                .andExpect(jsonPath("$.message").value("« Doliprane 500 mg » est déjà dans cette promotion : modifiez sa ligne."));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/activation"), "").andExpect(jsonPath("$.statut").value("ACTIVE"));

        // Les variables de promotion n'ont pas cours en Recouvrement
        envoyer(admin, post("/api/recouvrement/modeles-messages"), "{\"nom\":\"Relance promo\",\"typeCampagne\":\"APRES_ECHEANCE\",\"contenu\":\"{produits_promotion}\"}")
                .andExpect(jsonPath("$.message").value("Une relance ne cite ni promotion, ni événement, ni information : retirez {produits_promotion}."));
        Long modele = idDe(envoyer(charge, post("/api/marketing/modeles"), """
                {"nom":"Promo du mois","typeCampagne":"PROMOTION","contenu":"Bonjour {nom_pharmacie}, {promotion_nom} jusqu'au {promotion_fin} :\\n{produits_promotion}"}"""));
        idDe(envoyer(admin, post("/api/clients"), "{\"code\":\"P-1\",\"raisonSociale\":\"Pharmacie Un\",\"agenceId\":1,\"numeros\":{\"PRINCIPAL\":\"0700000001\"}}"));
        Long liste = idDe(envoyer(charge, post("/api/marketing/listes"), "{\"nom\":\"Toutes\",\"parCriteres\":true,\"fonctions\":[\"PRINCIPAL\"]}"));
        envoyer(charge, post("/api/marketing/campagnes"), "{\"nom\":\"Sans promo\",\"listeId\":" + liste + ",\"modeleId\":" + modele + "}")
                .andExpect(jsonPath("$.message").value("Le modèle cite {promotion_nom}, {promotion_fin}, {produits_promotion} : choisissez au moins une promotion."));
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"),
                "{\"nom\":\"Novembre\",\"listeId\":" + liste + ",\"modeleId\":" + modele + ",\"promotionIds\":[" + promo + "]}"));
        mvc.perform(get("/api/marketing/campagnes/" + campagne).session(charge))
                .andExpect(jsonPath("$.promotions", contains("NOV26 · Promo de novembre")));
        String fin = String.format("%02d/%02d/%d", AUJOURDHUI.plusDays(30).getDayOfMonth(), AUJOURDHUI.plusDays(30).getMonthValue(),
                AUJOURDHUI.plusDays(30).getYear());
        mvc.perform(get("/api/marketing/campagnes/" + campagne + "/recapitulatif").session(charge))
                .andExpect(jsonPath("$.exemples[0].contenu").value("Bonjour Pharmacie Un, Promo de novembre jusqu'au " + fin + " :\n"
                        + "• Amoxicilline 1 g : 2 200 F au lieu de 2 500 F\n• Doliprane 500 mg : 2 offerts pour 10 achetés"));

        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits/" + ligneDoliprane + "/retrait"), "")
                .andExpect(jsonPath("$.actif").value(false));
        Long ligneAmox = ((Number) JsonPath.read(mvc.perform(get("/api/marketing/promotions/" + promo + "/produits").session(charge))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits/" + ligneAmox + "/retrait"), "")
                .andExpect(jsonPath("$.message").value("Une promotion active garde au moins un produit : archivez-la plutôt."));
        mvc.perform(get("/api/marketing/promotions/" + promo + "/produits").param("avecRetires", "true").session(charge))
                .andExpect(jsonPath("$[*].actif", contains(true, false)));

        // Periode depassee : terminee d'office, la campagne ne peut plus partir ; prolongee, elle redevient active
        jdbc.update("update crm_promotion set date_debut = ?, date_fin = ? where id = ?", AUJOURDHUI.minusDays(10), AUJOURDHUI.minusDays(1), promo);
        assertThat(promotionService.terminerPromotionsEchues()).isEqualTo(1);
        mvc.perform(get("/api/marketing/promotions/" + promo).session(charge)).andExpect(jsonPath("$.statut").value("TERMINEE"));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "")
                .andExpect(jsonPath("$.message").value("La promotion « Promo de novembre » n'est pas en cours : choisissez une promotion active."));
        envoyer(charge, put("/api/marketing/promotions/" + promo), promotion("NOV26", "Promo de novembre", AUJOURDHUI.plusDays(5)))
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "").andExpect(jsonPath("$.statut").value("A_VALIDER"));

        envoyer(charge, post("/api/marketing/promotions/" + promo + "/archivage"), "").andExpect(jsonPath("$.statut").value("ARCHIVEE"));
        mvc.perform(get("/api/marketing/promotions").session(charge)).andExpect(jsonPath("$.length()").value(0));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/reactivation"), "").andExpect(jsonPath("$.statut").value("BROUILLON"));
        assertThat(jdbc.queryForList("select action from crm_journal_action where entite = 'crm_promotion' order by id", String.class))
                .contains("PROMOTION_CREATION", "PROMOTION_PRODUIT_AJOUT", "PROMOTION_ACTIVATION", "PROMOTION_PRODUIT_RETRAIT", "PROMOTION_FIN",
                        "PROMOTION_MODIFICATION", "PROMOTION_ARCHIVAGE", "PROMOTION_REACTIVATION");

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/marketing/promotions").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void lesProduitsDePlusieursPromotionsSImportentDUnSeulFichierEtSExportent() throws Exception {
        Long novembre = idDe(envoyer(charge, post("/api/marketing/promotions"), promotion("NOV26", "Novembre", AUJOURDHUI.plusDays(30))));
        idDe(envoyer(charge, post("/api/marketing/promotions"), promotion("DEC26", "Décembre", AUJOURDHUI.plusDays(60))));
        envoyer(charge, post("/api/marketing/promotions/" + novembre + "/produits"),
                "{\"produitId\":" + doliprane + ",\"quantiteMin\":10,\"avantage\":\"UG_QUANTITE\",\"ugQuantite\":1}").andExpect(status().isOk());

        String csv = """
                Code promo;Code article;Qté min;UG;Taux UG;Plafond UG;Prix promo
                NOV26;DOL500;10;2;;;
                NOV26;AMOX1;;;;;2000
                DEC26;VITC;20;;10;5;
                XX;DOL500;;2;;;
                NOV26;INCONNU;;2;;;
                DEC26;AMOX1;;2;;;1900
                NOV26;AMOX1;;;;;1800
                """;
        String simulation = mvc.perform(multipart("/api/marketing/imports/promotions").file(fichier(csv)).session(charge).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(2))
                .andExpect(jsonPath("$.nbMisesAJour").value(1))
                .andExpect(jsonPath("$.nbRejets").value(4))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(simulation, "$.id")).longValue();
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(charge))
                .andExpect(jsonPath("$.elements[*].message", contains("Promotion inconnue : XX. Créez-la d'abord dans Promotions.",
                        "Produit inconnu : INCONNU. Ajoutez-le d'abord au catalogue.",
                        "Choisissez entre unités gratuites et prix promotionnel, pas les deux.",
                        "Ce produit est déjà cité pour cette promotion ligne 3 du fichier.")));
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "MISE_A_JOUR").session(charge))
                .andExpect(jsonPath("$.elements[0].message").value("Avantage : 1 offert pour 10 achetés -> 2 offerts pour 10 achetés"));
        envoyer(charge, post("/api/marketing/imports/promotions/" + id + "/validation"), "{\"toutesLesMisesAJour\":true}")
                .andExpect(jsonPath("$.nbCrees").value(2))
                .andExpect(jsonPath("$.nbMisAJour").value(1));
        assertThat(jdbc.queryForObject("select count(*) from crm_promotion_produit where actif = true", Integer.class)).isEqualTo(3);

        String export = mvc.perform(get("/api/marketing/promotions/" + novembre + "/produits/export").session(charge))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(export).contains("Code promotion;Code produit;Libellé produit;Quantité minimale;Unités offertes")
                .contains("NOV26;AMOX1;Amoxicilline 1 g;1;;;;2000;2 000 F au lieu de 2 500 F")
                .contains("NOV26;DOL500;Doliprane 500 mg;10;2;;;;2 offerts pour 10 achetés");
        mvc.perform(get("/api/marketing/promotions").param("q", "DEC").session(charge))
                .andExpect(jsonPath("$[0].nbProduits").value(1));
        mvc.perform(get("/api/marketing/promotions/" + novembre + "/produits").session(charge))
                .andExpect(jsonPath("$[*].resume", contains("2 000 F au lieu de 2 500 F", "2 offerts pour 10 achetés")));
        mvc.perform(get("/api/imports").session(charge)).andExpect(jsonPath("$.elements[0].type").value("PROMOTIONS"));
        mvc.perform(get("/api/imports/promotions/champs").session(charge)).andExpect(jsonPath("$[0].libelle").value("Code promotion"));
        assertThat(jdbc.queryForObject("select ug_pourcentage from crm_promotion_produit pp join crm_produit p on p.id = pp.produit_id "
                + "where p.code = 'VITC'", String.class)).isEqualTo("10.00");
        mvc.perform(get("/api/marketing/promotions").param("q", "DEC").session(charge)).andExpect(jsonPath("$[0].statut").value("BROUILLON"));
        mvc.perform(get("/api/marketing/promotions/" + novembre + "/produits").session(admin)).andExpect(jsonPath("$.length()").value(2));
        envoyer(charge, post("/api/marketing/promotions"), promotion("nov26", "Doublon", AUJOURDHUI))
                .andExpect(jsonPath("$.message", containsString("est déjà utilisé par la promotion « Novembre »")));
    }

    private String promotion(String code, String nom, LocalDate fin) {
        return "{\"code\":\"" + code + "\",\"nom\":\"" + nom + "\",\"dateDebut\":\"" + AUJOURDHUI + "\",\"dateFin\":\"" + fin + "\"}";
    }

    private MockMultipartFile fichier(String contenu) {
        return new MockMultipartFile("fichier", "promotions.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        String corps = resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.id")).longValue();
    }
}
