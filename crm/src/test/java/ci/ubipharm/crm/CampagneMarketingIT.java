package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.CampagneMarketingService;
import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.SuiviEnvoiService;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
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

    @Autowired
    ci.ubipharm.crm.rest.service.PieceJointeService pieceJointeService;

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
        // « Envoyer maintenant » par son auteur sur le second message, hors de son tour ; le lot envoie l'autre
        Long second = jdbc.queryForObject("select max(id) from crm_campagne_message where campagne_id = ? and statut = 'A_ENVOYER'", Long.class, campagne);
        envoyer(charge, post("/api/envois/C" + second + "/immediat"), "").andExpect(jsonPath("$.statut").value("ENVOYE"));
        envoyer(charge, post("/api/envois/C" + second + "/immediat"), "").andExpect(status().isBadRequest());
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        assertThat(envoiWhatsappService.envoyerLot()).isZero();
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

    @Test
    void lAfficheEtLaListeDesProduitsPartentAvecLeMessage() throws Exception {
        Long produit = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"DOL500\",\"libelle\":\"Doliprane 500 mg\",\"prix\":1250}"));
        Long promo = idDe(envoyer(charge, post("/api/marketing/promotions"), """
                {"code":"NOV26","nom":"Promo de novembre","dateDebut":"%s","dateFin":"%s"}"""
                .formatted(java.time.LocalDate.now(java.time.ZoneOffset.UTC), java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(30))));
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/produits"),
                "{\"produitId\":" + produit + ",\"quantiteMin\":10,\"avantage\":\"UG_QUANTITE\",\"ugQuantite\":2}").andExpect(status().isOk());
        envoyer(charge, post("/api/marketing/promotions/" + promo + "/activation"), "").andExpect(status().isOk());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        mvc.perform(multipart("/api/marketing/pieces-jointes").file(new MockMultipartFile("fichier", "affiche.png", "image/png", "pas une image".getBytes()))
                .session(charge).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'affiche doit être une image JPEG ou PNG, ou un PDF."));
        String depot = mvc.perform(multipart("/api/marketing/pieces-jointes").file(new MockMultipartFile("fichier", "affiche.png", "image/png", png))
                .session(charge).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long affiche = ((Number) JsonPath.read(depot, "$.id")).longValue();

        envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Sans promotion","listeId":%d,"modeleId":%d,"joindreProduits":true}""".formatted(liste, modele))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cochez au moins une promotion ou un produit de retour en stock pour joindre la liste des produits."));
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Novembre","listeId":%d,"modeleId":%d,"promotionIds":[%d],"afficheId":%d,"joindreProduits":true}"""
                .formatted(liste, modele, promo, affiche)));
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "");
        envoyer(admin, post("/api/marketing/campagnes/" + campagne + "/validation"), "")
                .andExpect(jsonPath("$.afficheNom").value("affiche.png"))
                .andExpect(jsonPath("$.listeProduitsId").isNumber());

        jdbc.update("insert into crm_session_whatsapp (numero, statut, updated_at) values (?, 'CONNECTE', utc_timestamp())", NUMERO_MARKETING);
        when(passerelle.envoyerFichier(eq(NUMERO_MARKETING), anyString(), any(), any()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("WM-1").build());
        assertThat(envoiWhatsappService.envoyerLot()).isEqualTo(1);
        ArgumentCaptor<FichierDTO> fichiers = ArgumentCaptor.forClass(FichierDTO.class);
        ArgumentCaptor<String> legendes = ArgumentCaptor.forClass(String.class);
        verify(passerelle, times(2)).envoyerFichier(eq(NUMERO_MARKETING), eq("2250700000001"), fichiers.capture(), legendes.capture());
        verify(passerelle, never()).envoyerTexte(anyString(), anyString(), anyString());
        assertThat(fichiers.getAllValues()).extracting(FichierDTO::getNom).containsExactly("affiche.png", "produits_en_promotion.xlsx");
        assertThat(legendes.getAllValues().getFirst()).startsWith("Bonjour Pharmacie Deux Numeros");
        assertThat(legendes.getAllValues().get(1)).isNull();
        try (var classeur = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(fichiers.getAllValues().get(1).getContenu()))) {
            var ligne = classeur.getSheetAt(0).getRow(1);
            assertThat(ligne.getCell(3).getStringCellValue()).isEqualTo("Doliprane 500 mg");
            assertThat(ligne.getCell(7).getStringCellValue()).isEqualTo("2 offerts pour 10 achetés");
        }

        // Un SMS ne transporte pas de fichier
        Long sms = idDe(envoyer(admin, post("/api/marketing/modeles"), """
                {"nom":"Promo SMS","typeCampagne":"PROMOTION","canal":"SMS","contenu":"Promo {nom_pharmacie}"}"""));
        envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"SMS","listeId":%d,"modeleId":%d,"afficheId":%d}""".formatted(liste, sms, affiche))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Un SMS ne transporte pas de pièce jointe : retirez l'affiche et la liste des produits."));
    }

    @Test
    void unMessageDeDisponibiliteAnnonceLesProduitsDeRetourEnStock() throws Exception {
        Long dol = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"DOL500\",\"libelle\":\"Doliprane 500 mg\",\"conditionnement\":\"Boîte de 16\"}"));
        Long amox = idDe(envoyer(charge, post("/api/marketing/produits"), "{\"code\":\"AMOX1\",\"libelle\":\"Amoxicilline 1 g\"}"));
        envoyer(charge, post("/api/marketing/produits/" + dol + "/retour-stock"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le produit DOL500 est déjà disponible."));
        envoyer(charge, post("/api/marketing/produits/" + dol + "/rupture"), "").andExpect(jsonPath("$.disponibilite").value("RUPTURE"));
        envoyer(charge, post("/api/marketing/produits/" + amox + "/rupture"), "");
        envoyer(charge, post("/api/marketing/produits/" + dol + "/retour-stock"), "")
                .andExpect(jsonPath("$.disponibilite").value("DISPONIBLE")).andExpect(jsonPath("$.deRetour").value(true));
        mvc.perform(get("/api/marketing/produits").param("disponibilite", "RETOUR").session(charge))
                .andExpect(jsonPath("$.elements[*].code", org.hamcrest.Matchers.contains("DOL500")));

        Long retour = idDe(envoyer(charge, post("/api/marketing/modeles"), """
                {"nom":"Retour en stock","typeCampagne":"PROMOTION","contenu":"Bonjour {nom_pharmacie}, de retour en stock :\\n{produits_disponibles}"}"""));
        envoyer(charge, post("/api/marketing/campagnes"), campagne("Sans produit", retour)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le modèle cite {produits_disponibles} : choisissez au moins un produit de retour en stock."));
        envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Rupture","listeId":%d,"modeleId":%d,"produitIds":[%d]}""".formatted(liste, retour, amox))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le produit AMOX1 est de nouveau en rupture : retirez-le du message."));
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"De retour","listeId":%d,"modeleId":%d,"produitIds":[%d],"joindreProduits":true}""".formatted(liste, retour, dol))
                .andExpect(jsonPath("$.produits[0]").value("DOL500 · Doliprane 500 mg")));
        mvc.perform(get("/api/marketing/campagnes/" + campagne + "/recapitulatif").session(charge))
                .andExpect(jsonPath("$.exemples[0].contenu").value("Bonjour Pharmacie Deux Numeros, de retour en stock :\n• Doliprane 500 mg, Boîte de 16"));

        // Le produit repart en rupture avant la validation : la campagne ne part pas
        envoyer(charge, post("/api/marketing/campagnes/" + campagne + "/soumission"), "").andExpect(status().isOk());
        envoyer(charge, post("/api/marketing/produits/" + dol + "/rupture"), "");
        envoyer(admin, post("/api/marketing/campagnes/" + campagne + "/validation"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le produit DOL500 est de nouveau en rupture : retirez-le du message."));
        envoyer(charge, post("/api/marketing/produits/" + dol + "/retour-stock"), "");
        envoyer(admin, post("/api/marketing/campagnes/" + campagne + "/validation"), "")
                .andExpect(jsonPath("$.listeProduitsId").isNumber());
        String nom = jdbc.queryForObject("select pj.nom_fichier from crm_campagne c join crm_piece_jointe pj on pj.id = c.liste_produits_id where c.id = ?",
                String.class, campagne);
        assertThat(nom).isEqualTo("produits_disponibles.xlsx");

        // Un modele de relance ne cite pas les produits disponibles
        envoyer(admin, post("/api/recouvrement/modeles-messages"), """
                {"nom":"Relance dispo","typeCampagne":"APRES_ECHEANCE","contenu":"{produits_disponibles}"}""").andExpect(status().isBadRequest());
    }

    @Test
    void unMessageDEvenementAnnonceLaFermetureEtSeRepeteChaqueAnnee() throws Exception {
        java.time.LocalDate jour = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.format.DateTimeFormatter lisible = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        // Les jours feries a date fixe sont livres et reviennent chaque annee
        mvc.perform(get("/api/marketing/evenements").session(charge))
                .andExpect(jsonPath("$[?(@.nom == 'Noël')].annuel").value(true))
                .andExpect(jsonPath("$[?(@.nom == 'Noël')].prochaineDate").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.endsWith("-12-25"))));
        envoyer(charge, post("/api/marketing/evenements"), """
                {"nom":"Ancien","nature":"AUTRE","dateDebut":"%s"}""".formatted(jour.minusDays(3)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cet événement est déjà passé."));
        java.time.LocalDate debut = jour.plusDays(10);
        Long inventaire = idDe(envoyer(charge, post("/api/marketing/evenements"), """
                {"nom":"Inventaire annuel","nature":"FERMETURE","dateDebut":"%s","dateFin":"%s","annuel":true}"""
                .formatted(debut, debut.plusDays(2)))
                .andExpect(jsonPath("$.prochaineDate").value(debut.toString()))
                .andExpect(jsonPath("$.envoiPropose").value(debut.minusDays(3) + "T08:00:00")));

        Long fermeture = idDe(envoyer(charge, post("/api/marketing/modeles"), """
                {"nom":"Fermeture inventaire","typeCampagne":"EVENEMENT","contenu":"Bonjour {nom_pharmacie}, agence fermée du {evenement_date} au {evenement_fin} ({evenement_nom})."}""")
                .andExpect(jsonPath("$.typeCampagne").value("EVENEMENT")));
        envoyer(charge, post("/api/marketing/campagnes"), campagne("Sans evenement", fermeture)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le modèle cite {evenement_date}, {evenement_fin}, {evenement_nom} : choisissez l'événement annoncé."));
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Inventaire","listeId":%d,"modeleId":%d,"evenementId":%d,"dateExecution":"%sT08:00:00",
                 "recurrence":"ANNUELLE","finRecurrence":"%s"}""".formatted(liste, fermeture, inventaire, debut.minusDays(1), debut.plusYears(3)))
                .andExpect(jsonPath("$.typeCampagne").value("EVENEMENT"))
                .andExpect(jsonPath("$.evenementNom").value("Inventaire annuel")));
        mvc.perform(get("/api/marketing/campagnes/" + campagne + "/recapitulatif").session(charge))
                .andExpect(jsonPath("$.exemples[0].contenu").value("Bonjour Pharmacie Deux Numeros, agence fermée du " + debut.format(lisible)
                        + " au " + debut.plusDays(2).format(lisible) + " (Inventaire annuel)."));

        // L'evenement annonce ne s'archive pas tant que la campagne peut partir
        envoyer(charge, post("/api/marketing/evenements/" + inventaire + "/archivage"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'événement est annoncé par des campagnes en préparation ou programmées : Inventaire. "
                        + "Annulez-les d'abord."));
        // Un modele de relance ne cite pas d'evenement
        envoyer(admin, post("/api/recouvrement/modeles-messages"), """
                {"nom":"Relance fete","typeCampagne":"APRES_ECHEANCE","contenu":"{evenement_nom}"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Une relance ne cite ni promotion, ni événement, ni information : retirez {evenement_nom}."));
    }

    @Test
    void unRetardDeLivraisonSAnnonceAuxClientsALHeureProgrammee() throws Exception {
        envoyer(charge, post("/api/marketing/informations"), """
                {"titre":"Retard","typeInformation":"RETARD_LIVRAISON"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Précisez l'agence ou la tournée concernée par la livraison."));
        envoyer(charge, post("/api/marketing/informations"), """
                {"titre":"Annonce","typeInformation":"INFORMATION_GENERALE"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saisissez le message : le modèle de ce type d'information le cite."));
        Long retard = idDe(envoyer(charge, post("/api/marketing/informations"), """
                {"titre":"Camion en panne","typeInformation":"RETARD_LIVRAISON","priorite":"URGENTE","agenceId":1,
                 "tournee":"Tournée Yopougon Nord","nouvelleHeure":"15 h","causeInterne":"Batterie du camion 3"}""")
                .andExpect(jsonPath("$.priorite").value("URGENTE")));

        Long modeleRetard = idDe(envoyer(charge, post("/api/marketing/modeles"), """
                {"nom":"Retard court","typeCampagne":"INFORMATION","typeInformation":"RETARD_LIVRAISON",
                 "contenu":"Bonjour {nom_pharmacie},\\nretard sur {agence_ou_tournee}.\\nCause : {cause_retard}\\n{nouvelle_estimation_livraison}\\n{direction_signataire}"}""")
                .andExpect(jsonPath("$.typeCampagne").value("INFORMATION"))
                .andExpect(jsonPath("$.typeInformation").value("RETARD_LIVRAISON")));
        envoyer(charge, post("/api/marketing/campagnes"), campagne("Sans information", modeleRetard)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le modèle cite {agence_ou_tournee}, {cause_retard}, {nouvelle_estimation_livraison}, "
                        + "{direction_signataire} : choisissez l'information annoncée."));
        java.time.LocalDateTime heure = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).atTime(7, 30);
        Long campagne = idDe(envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Retard camion","listeId":%d,"modeleId":%d,"informationId":%d,"dateExecution":"%s"}""".formatted(liste, modeleRetard, retard, heure))
                .andExpect(jsonPath("$.typeCampagne").value("INFORMATION"))
                .andExpect(jsonPath("$.informationTitre").value("Camion en panne"))
                .andExpect(jsonPath("$.dateExecution").value(heure + ":00")));
        String agence = jdbc.queryForObject("select libelle from crm_agence where id = 1", String.class);
        // La cause n'est pas communiquee : sa ligne disparait ; la cause interne ne part jamais
        mvc.perform(get("/api/marketing/campagnes/" + campagne + "/recapitulatif").session(charge))
                .andExpect(jsonPath("$.exemples[0].contenu").value("Bonjour Pharmacie Deux Numeros,\nretard sur Tournée Yopougon Nord.\n"
                        + "Nouvelle estimation de livraison : 15 h\nDirection " + agence));

        // La banniere de la charte part en tete de l'information sans affiche, sauf si le parametre l'ecarte
        assertThat(pieceJointeService.piecesDeCampagne(campagne)).isEmpty();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        mvc.perform(multipart("/api/charte/banniere").file(new MockMultipartFile("fichier", "banniere.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes())).session(admin).with(csrf()))
                .andExpect(jsonPath("$.message").value("La bannière doit être une image PNG ou JPEG : elle part dans les messages WhatsApp."));
        mvc.perform(multipart("/api/charte/banniere").file(new MockMultipartFile("fichier", "banniere.png", "image/png", png)).session(admin).with(csrf()))
                .andExpect(jsonPath("$.banniereNom").value("banniere.png"));
        assertThat(pieceJointeService.piecesDeCampagne(campagne)).extracting(FichierDTO::getNom).containsExactly("banniere.png");
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'marketing.banniere_informations'");
        assertThat(pieceJointeService.piecesDeCampagne(campagne)).isEmpty();
        jdbc.update("update crm_parametre set valeur = 'OUI' where cle = 'marketing.banniere_informations'");
        jdbc.update("update crm_charte_fichier set actif = false where code = 'BANNIERE'");

        mvc.perform(get("/api/marketing/informations").session(charge))
                .andExpect(jsonPath("$[0].nbCampagnes").value(1)).andExpect(jsonPath("$[0].derniereCampagne").value("Retard camion"));
        envoyer(charge, post("/api/marketing/informations/" + retard + "/archivage"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'information est annoncée par des campagnes en préparation ou programmées : Retard camion. "
                        + "Annulez-les d'abord."));
        Long copie = idDe(envoyer(charge, post("/api/marketing/informations/" + retard + "/duplication"), "")
                .andExpect(jsonPath("$.titre").value("Camion en panne (copie)")).andExpect(jsonPath("$.tournee").value("Tournée Yopougon Nord")));
        jdbc.update("update crm_information set fin_validite = utc_date() - interval 1 day where id = ?", copie);
        envoyer(charge, post("/api/marketing/campagnes"), """
                {"nom":"Trop tard","listeId":%d,"modeleId":%d,"informationId":%d}""".formatted(liste, modeleRetard, copie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("L'information « Camion en panne (copie) » n'est plus valable")));
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
