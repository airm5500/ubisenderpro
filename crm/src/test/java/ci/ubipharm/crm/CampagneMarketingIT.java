package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.CampagneMarketingService;
import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** Campagnes promotionnelles : liste de diffusion, modele du Marketing, numero de l'auteur, validation par agence. */
class CampagneMarketingIT extends IntegrationBase {

    private static final String NUMERO_MARKETING = "2250722222222";

    @MockitoBean
    PasserelleWhatsappService passerelle;

    @Autowired
    EnvoiWhatsappService envoiWhatsappService;

    @Autowired
    CampagneMarketingService campagneMarketingService;

    @Autowired
    SuiviEnvoiService suiviEnvoiService;

    private MockHttpSession admin;
    private MockHttpSession charge;
    private Long liste;
    private Long modele;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        for (String[] p : new String[][] {{"whatsapp.envoi.heure_debut", "0"}, {"whatsapp.envoi.heure_fin", "24"},
                {"whatsapp.envoi.delai_min_secondes", "0"}, {"whatsapp.envoi.delai_max_secondes", "0"}, {"whatsapp.envoi.plafond_jour", "200"}}) {
            jdbc.update("update crm_parametre set valeur = ? where cle = ?", p[1], p[0]);
        }
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        jdbc.update("update crm_utilisateur set numero_whatsapp = ?, email = 'marketing@ubipharm.ci' where login = 'it-marketing'", NUMERO_MARKETING);
        charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);

        idDe(envoyer(admin, post("/api/clients"), """
                {"code":"P-1","raisonSociale":"Pharmacie Deux Numeros","agenceId":1,"numeros":{"PRINCIPAL":"0700000001","TITULAIRE":"0700000002"}}"""));
        Long desabonne = idDe(envoyer(admin, post("/api/clients"), """
                {"code":"P-2","raisonSociale":"Pharmacie Desabonnee","agenceId":1,"numeros":{"PRINCIPAL":"0700000003"}}"""));
        idDe(envoyer(admin, post("/api/clients"), """
                {"code":"P-3","raisonSociale":"Pharmacie Sans Numero","agenceId":1,"email":"p3@exemple.ci"}"""));
        idDe(envoyer(admin, post("/api/clients"), """
                {"code":"P-4","raisonSociale":"Pharmacie Vridi","agenceId":2,"numeros":{"PRINCIPAL":"0700000004"},"email":"p4@exemple.ci"}"""));
        envoyer(admin, post("/api/marketing/clients/" + desabonne + "/desabonnement"), "{\"motif\":\"Demande du client\"}").andExpect(status().isOk());

        liste = idDe(envoyer(admin, post("/api/marketing/listes"), "{\"nom\":\"Toutes\",\"parCriteres\":true,\"fonctions\":[\"PRINCIPAL\",\"TITULAIRE\"]}"));
        modele = idDe(envoyer(charge, post("/api/marketing/modeles"), """
                {"nom":"Promo","typeCampagne":"PROMOTION","contenu":"Bonjour {nom_pharmacie}, promotion de la semaine. {expediteur_nom}"}"""));
    }

    @Test
    void laPromotionPartDuNumeroDuChargeApresValidationParSonDirecteur() throws Exception {
        envoyer(charge, post("/api/marketing/modeles"), "{\"nom\":\"Mauvais\",\"typeCampagne\":\"PROMOTION\",\"contenu\":\"Solde {solde}\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Une promotion ne porte sur aucune facture ni agent de recouvrement : retirez {solde}."));

        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), campagne("Semaine 43", modele)));
        mvc.perform(get("/api/marketing/campagnes/" + campagne + "/recapitulatif").session(charge))
                .andExpect(jsonPath("$.nbClients").value(1))
                .andExpect(jsonPath("$.nbMessages").value(2))
                .andExpect(jsonPath("$.nbExclus").value(2))
                .andExpect(jsonPath("$.numeroExpediteur").value(NUMERO_MARKETING))
                .andExpect(jsonPath("$.exemples[0].contenu").value("Bonjour Pharmacie Deux Numeros, promotion de la semaine. Test it-marketing"))
                .andExpect(jsonPath("$.exclusions[?(@.motif == 'Client désabonné (STOP)')].nb").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.exclusions[?(@.motif == 'Client sans numéro WhatsApp')].nb").value(org.hamcrest.Matchers.contains(1)));

        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "").andExpect(jsonPath("$.statut").value("A_VALIDER"));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/validation"), "").andExpect(status().isForbidden());
        mvc.perform(get("/api/recouvrement/campagnes").session(admin)).andExpect(jsonPath("$.total").value(0));

        creerUtilisateur("it-directeur", "DIR_AGENCE", "MARKETING", 1L);
        MockHttpSession directeur = connecter("it-directeur", MOT_DE_PASSE_ADMIN);
        envoyer(directeur, post("/api/marketing/campagnes/" + campagne + "/validation"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.nbMessages").value(2));

        jdbc.update("insert into crm_session_whatsapp (numero, statut, updated_at) values (?, 'CONNECTE', utc_timestamp())", NUMERO_MARKETING);
        when(passerelle.envoyerTexte(eq(NUMERO_MARKETING), anyString(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("WM-1").build(),
                        ResultatEnvoiDTO.builder().succes(true).identifiant("WM-2").build());
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(jdbc.queryForList("select numero_destinataire from crm_campagne_message where campagne_id = ? and statut = 'ENVOYE' order by id",
                String.class, campagne)).containsExactly("2250700000001", "2250700000002");
        mvc.perform(get("/api/marketing/campagnes/" + campagne).session(charge))
                .andExpect(jsonPath("$.statut").value("TERMINEE"))
                .andExpect(jsonPath("$.nbEnvoyes").value(2));
    }

    @Test
    void sansNumeroWhatsappRienNeSeSoumetEtUnDirecteurNeValidePasHorsDeSesAgences() throws Exception {
        jdbc.update("update crm_utilisateur set numero_whatsapp = null where login = 'it-marketing'");
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), campagne("Sans numero", modele)));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Le compte de Test it-marketing n'a pas de numéro WhatsApp")));

        // L'administrateur voit toutes les agences ; l'e-mail part a l'adresse de la fiche, reponse a l'auteur
        Long courriel = idDe(envoyer(admin, post("/api/marketing/modeles"), """
                {"nom":"Promo mail","typeCampagne":"PROMOTION","canal":"EMAIL","objet":"Offre {agence}","contenu":"Bonjour {nom_pharmacie}"}"""));
        Long globale = idDe(envoyer(admin, post("/api/marketing/campagnes"), campagne("Toutes agences", courriel)));
        envoyer(admin, post("/api/marketing/campagnes/" + globale + "/soumission"), "")
                .andExpect(jsonPath("$.nbMessages").value(2))
                .andExpect(jsonPath("$.agencesCibles.length()").value(2));

        creerUtilisateur("it-directeur", "DIR_AGENCE", "MARKETING", 1L);
        MockHttpSession directeur = connecter("it-directeur", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/marketing/campagnes/" + globale + "/messages").session(directeur))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.elements[?(@.statut == 'A_ENVOYER')].emailDestinataire").value(org.hamcrest.Matchers.contains("p3@exemple.ci")));
        envoyer(directeur, post("/api/marketing/campagnes/" + globale + "/validation"), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La campagne vise des clients d'autres agences que les vôtres : elle se valide par la direction."));
        envoyer(admin, post("/api/marketing/campagnes/" + globale + "/validation"), "").andExpect(jsonPath("$.statut").value("EN_COURS"));
        assertThat(jdbc.queryForList("select objet from crm_campagne_message where campagne_id = ? and statut = 'A_ENVOYER' order by id",
                String.class, globale)).containsExactly("Offre Yopougon", "Offre Vridi");

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        mvc.perform(get("/api/marketing/campagnes").session(connecter("it-agent", MOT_DE_PASSE_ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void uneCampagneRecurrenteRepartChaqueSemaineEtSArreteSiSonAuteurNePeutPlusEnvoyer() throws Exception {
        String fin = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusMonths(1).toString();
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Chaque lundi","listeId":%d,"modeleId":%d,"recurrence":"HEBDOMADAIRE","finRecurrence":"%s"}""".formatted(liste, modele, fin)));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "");
        envoyer(admin, post("/api/marketing/campagnes/" + campagne + "/validation"), "")
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.nbExecutions").value(1));
        jdbc.update("update crm_campagne_message set statut = 'ENVOYE' where campagne_id = ? and statut = 'A_ENVOYER'", campagne);
        suiviEnvoiService.recompter(campagne);
        mvc.perform(get("/api/marketing/campagnes/" + campagne).session(charge)).andExpect(jsonPath("$.statut").value("VALIDEE"));

        jdbc.update("update crm_campagne set date_execution = utc_timestamp() - interval 1 minute where id = ?", campagne);
        assertThat(campagneMarketingService.demarrerCampagnesEchues()).isEqualTo(1);
        mvc.perform(get("/api/marketing/campagnes/" + campagne).session(charge))
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.nbExecutions").value(2))
                .andExpect(jsonPath("$.nbMessages").value(4));

        jdbc.update("update crm_campagne_message set statut = 'ENVOYE' where campagne_id = ? and statut = 'A_ENVOYER'", campagne);
        suiviEnvoiService.recompter(campagne);
        jdbc.update("update crm_utilisateur set numero_whatsapp = null where login = 'it-marketing'");
        jdbc.update("update crm_campagne set date_execution = utc_timestamp() - interval 1 minute where id = ?", campagne);
        campagneMarketingService.demarrerCampagnesEchues();
        mvc.perform(get("/api/marketing/campagnes/" + campagne).session(admin))
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.motifAnnulation", org.hamcrest.Matchers.endsWith("Répétition arrêtée.")));
    }

    private String campagne(String nom, Long modeleId) {
        return "{\"nom\":\"" + nom + "\",\"listeId\":" + liste + ",\"modeleId\":" + modeleId + "}";
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        String corps = resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.id")).longValue();
    }
}
