package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ci.ubipharm.crm.rest.service.EnvoiMasseService;
import ci.ubipharm.crm.rest.service.EvenementWhatsappService;
import ci.ubipharm.crm.rest.service.PasserelleWhatsappService;
import ci.ubipharm.crm.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementMessageDTO;
import ci.ubipharm.crm.rest.service.dto.FichierDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatEnvoiDTO;

/** Envoi WhatsApp en masse, discussions, historique des envois, historique d'un compte. */
class EchangesIT extends IntegrationBase {

    private static final String NUMERO = "2250722222222";

    @MockitoBean
    PasserelleWhatsappService passerelle;

    @Autowired
    EnvoiMasseService envoiMasseService;

    @Autowired
    EvenementWhatsappService evenementWhatsappService;

    private MockHttpSession admin;
    private MockHttpSession charge;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        for (String[] p : new String[][] {{"whatsapp.envoi.heure_debut", "0"}, {"whatsapp.envoi.heure_fin", "24"},
                {"whatsapp.envoi.delai_min_secondes", "0"}, {"whatsapp.envoi.delai_max_secondes", "0"}, {"whatsapp.envoi.plafond_jour", "200"},
                {"discussions.reponse_active", "OUI"}, {"whatsapp.envoi_masse.heure_debut", "0"}, {"whatsapp.envoi_masse.heure_fin", "24"}}) {
            jdbc.update("update crm_parametre set valeur = ? where cle = ?", p[1], p[0]);
        }
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
        creerUtilisateur("it-marketing", "CHARGE_MARKETING", "MARKETING", 1L);
        jdbc.update("update crm_utilisateur set numero_whatsapp = ? where login = 'it-marketing'", NUMERO);
        charge = connecter("it-marketing", MOT_DE_PASSE_ADMIN);
        jdbc.update("insert into crm_session_whatsapp (numero, statut, sante, updated_at) values (?, 'CONNECTE', 'OK', utc_timestamp())", NUMERO);
        envoyer(admin, post("/api/clients"), """
                {"code":"E-1","raisonSociale":"Pharmacie Un","agenceId":1,"numeros":{"PRINCIPAL":"0700000001"}}""").andExpect(status().isOk());
        Long stop = idDe(envoyer(admin, post("/api/clients"), """
                {"code":"E-2","raisonSociale":"Pharmacie Stop","agenceId":1,"numeros":{"PRINCIPAL":"0700000002"}}"""));
        envoyer(admin, post("/api/marketing/clients/" + stop + "/desabonnement"), "{\"motif\":\"STOP\"}").andExpect(status().isOk());
        Long ferme = idDe(envoyer(admin, post("/api/clients"), """
                {"code":"E-3","raisonSociale":"Pharmacie Fermee","agenceId":1,"numeros":{"PRINCIPAL":"0700000003"}}"""));
        envoyer(admin, post("/api/clients/" + ferme + "/desactivation"), "{\"motif\":\"Fermée\"}").andExpect(status().isOk());
    }

    @Test
    void unEnvoiEnMasseExclutLesDesabonnesPartAuDebitDesRelancesEtSeRetrouveDansLHistorique() throws Exception {
        String numeros = "0700000001\n07 00 00 00 09;Dr Kouassi\n0700000002\n0700000003\n123\n0700000009";
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Rappel\",\"contenu\":\"Bonjour {nom}, nouvel arrivage.\",\"numeros\":\"%s\",\"apercu\":true}"
                .formatted(numeros.replace("\n", "\\n")))
                .andExpect(jsonPath("$.nbRetenus").value(2))
                .andExpect(jsonPath("$.exemple").value("Bonjour Pharmacie Un, nouvel arrivage."))
                .andExpect(jsonPath("$.exclus[*].motif").value(org.hamcrest.Matchers.containsInAnyOrder("Désabonné (STOP)", "Client désactivé",
                        "Format invalide", "En double")));
        assertThat(jdbc.queryForObject("select count(*) from crm_envoi_masse", Integer.class)).isZero();

        Long envoi = ((Number) JsonPath.read(envoyer(charge, post("/api/envois-masse"),
                "{\"nom\":\"Rappel\",\"contenu\":\"Bonjour {nom}, nouvel arrivage.\",\"numeros\":\"%s\"}".formatted(numeros.replace("\n", "\\n")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.envoi.id")).longValue();

        when(passerelle.envoyerTexte(eq(NUMERO), anyString(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("M-1").build(), ResultatEnvoiDTO.builder().succes(true).identifiant("M-2").build());
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        assertThat(envoiMasseService.envoyerLot()).isZero();
        mvc.perform(get("/api/envois-masse/" + envoi).session(charge))
                .andExpect(jsonPath("$.statut").value("TERMINE"))
                .andExpect(jsonPath("$.nbEnvoyes").value(2))
                .andExpect(jsonPath("$.nbExclus").value(4));
        assertThat(jdbc.queryForObject("select contenu from crm_message_sortant where numero_destinataire = '2250700000009'", String.class))
                .isEqualTo("Bonjour Dr Kouassi, nouvel arrivage.");

        // Accuse de lecture, puis reponse du client : la discussion s'ouvre
        evenementWhatsappService.accuse(EvenementAccuseDTO.builder().sessionId(NUMERO).id("M-1").status("LU").build());
        evenementWhatsappService.message(EvenementMessageDTO.builder().sessionId(NUMERO).id("R-1").from("2250700000009").name("Kouassi")
                .type("TEXTE").text("Merci, je passe commande").build());

        mvc.perform(get("/api/historique-envois").param("origine", "ENVOI_MASSE").session(charge))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.elements[?(@.destinataire == '2250700000001')].statut").value("LU"));
        mvc.perform(get("/api/historique-envois/export").param("origine", "ENVOI_MASSE").session(charge))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Désabonné (STOP)")));

        mvc.perform(get("/api/discussions").session(charge))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].numeroClient").value("2250700000009"))
                .andExpect(jsonPath("$.elements[0].nomAffiche").value("Kouassi"))
                .andExpect(jsonPath("$.elements[0].dernierSens").value("ENTRANT"))
                .andExpect(jsonPath("$.elements[0].peutRepondre").value(true));
        when(passerelle.envoyerTexte(NUMERO, "2250700000009", "Bien reçu, merci."))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("M-3").build());
        envoyer(charge, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Bien reçu, merci.\"}"
                .formatted(NUMERO)).andExpect(jsonPath("$.origine").value("DISCUSSION"));
        mvc.perform(get("/api/discussions/messages").param("numeroAgent", NUMERO).param("numeroClient", "2250700000009").session(charge))
                .andExpect(jsonPath("$[*].sens").value(org.hamcrest.Matchers.contains("SORTANT", "ENTRANT", "SORTANT")))
                .andExpect(jsonPath("$[2].contenu").value("Bien reçu, merci."));

        // L'administrateur n'a pas ce numero : il lit, il ne repond pas
        envoyer(admin, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Test\"}".formatted(NUMERO))
                .andExpect(jsonPath("$.message").value("Vous ne pouvez répondre que depuis votre numéro ou celui d'un agent que vous remplacez."));
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'discussions.reponse_active'");
        envoyer(charge, post("/api/discussions/messages"), "{\"numeroAgent\":\"%s\",\"numeroClient\":\"2250700000009\",\"contenu\":\"Test\"}".formatted(NUMERO))
                .andExpect(jsonPath("$.message", containsString("désactivée")));

        // L'annulation arrete ce qui n'est pas parti
        Long second = ((Number) JsonPath.read(envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Second\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andReturn().getResponse().getContentAsString(), "$.envoi.id")).longValue();
        envoyer(charge, post("/api/envois-masse/" + second + "/annulation"), "").andExpect(jsonPath("$.statut").value("ANNULE"));
        assertThat(envoiMasseService.envoyerLot()).isZero();
    }

    @Test
    void lEnvoiEnMassePartDUnModeleAvecSesVariablesSesPiecesJointesEtDesClientsChoisis() throws Exception {
        Long modele = idDe(envoyer(admin, post("/api/marketing/modeles"), """
                {"nom":"Arrivage","typeCampagne":"PROMOTION","contenu":"Bonjour *{nom_pharmacie}* ({code_client}),\\nnouvel arrivage.\\nPromo : {promotion_nom}\\n{expediteur_nom}"}"""));
        mvc.perform(get("/api/envois-masse/references").session(charge))
                .andExpect(jsonPath("$.modeles[?(@.id == %d)].nom".formatted(modele)).value("Arrivage"))
                .andExpect(jsonPath("$.variables[*].code").value(org.hamcrest.Matchers.hasItems("nom", "nom_pharmacie", "code_client")))
                .andExpect(jsonPath("$.variables[*].code").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("promotion_nom"))));

        // Fichier de destinataires : lu, rien n'est enregistre ; une ligne sans numero est a corriger
        mvc.perform(multipart("/api/envois-masse/lecture-fichier").file(new MockMultipartFile("fichier", "dest.csv", "text/csv",
                        "Téléphone;Nom\n0700000009;Dr Kouassi\nabc;Dr Faux\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))).session(charge).with(csrf()))
                .andExpect(jsonPath("$.valides[0].numero").value("2250700000009")).andExpect(jsonPath("$.valides[0].nom").value("Dr Kouassi"))
                .andExpect(jsonPath("$.invalides[0].saisi").value("abc"));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        Long image = idDe(mvc.perform(multipart("/api/envois-masse/pieces-jointes").file(new MockMultipartFile("fichier", "arrivage.png", "image/png", png))
                .session(charge).with(csrf())));
        Long pdf = idDe(mvc.perform(multipart("/api/envois-masse/pieces-jointes").file(new MockMultipartFile("fichier", "tarifs.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes())).session(charge).with(csrf())));
        mvc.perform(multipart("/api/envois-masse/pieces-jointes").file(new MockMultipartFile("fichier", "x.exe", "application/octet-stream", "MZ".getBytes()))
                .session(charge).with(csrf())).andExpect(status().isBadRequest());

        Long client = jdbc.queryForObject("select id from crm_client where code = 'E-1'", Long.class);
        String corps = """
                {"nom":"Arrivage","modeleId":%d,"contenu":"Bonjour *{nom_pharmacie}* ({code_client}),\\nnouvel arrivage.\\nPromo : {promotion_nom}\\n{expediteur_nom}",
                 "clientIds":[%d],"numeros":"0700000009;Dr Kouassi","pieceJointeIds":[%d,%d]%s}""";
        envoyer(charge, post("/api/envois-masse"), corps.formatted(modele, client, image, pdf, ",\"apercu\":true"))
                .andExpect(jsonPath("$.nbRetenus").value(2))
                .andExpect(jsonPath("$.variablesRetirees[0]").value("promotion_nom"))
                .andExpect(jsonPath("$.piecesJointes[*].nomFichier").value(org.hamcrest.Matchers.contains("arrivage.png", "tarifs.pdf")))
                .andExpect(jsonPath("$.exemple").value("Bonjour *Dr Kouassi* (),\nnouvel arrivage.\nTest it-marketing"));

        // Depart programme : rien ne part avant l'heure
        Long envoi = ((Number) JsonPath.read(envoyer(charge, post("/api/envois-masse"),
                corps.formatted(modele, client, image, pdf, ",\"departLe\":\"" + java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(2).withNano(0) + "\""))
                .andExpect(jsonPath("$.envoi.modeleNom").value("Arrivage")).andExpect(jsonPath("$.envoi.departLe").isNotEmpty())
                .andReturn().getResponse().getContentAsString(), "$.envoi.id")).longValue();
        assertThat(envoiMasseService.envoyerLot()).isZero();
        assertThat(jdbc.queryForObject("select contenu from crm_message_sortant where numero_destinataire = '2250700000001'", String.class))
                .isEqualTo("Bonjour *Pharmacie Un* (E-1),\nnouvel arrivage.\nTest it-marketing");

        // A l'heure : l'image porte le texte en legende, le PDF suit ; un echec se renvoie
        jdbc.update("update crm_envoi_masse set depart_le = utc_timestamp() - interval 1 minute where id = ?", envoi);
        when(passerelle.envoyerFichier(eq(NUMERO), anyString(), any(), any()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("F-1").build());
        when(passerelle.envoyerFichier(eq(NUMERO), eq("2250700000009"), any(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(false).erreur("Numéro inconnu de WhatsApp").build());
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        org.mockito.ArgumentCaptor<FichierDTO> fichiers = org.mockito.ArgumentCaptor.forClass(FichierDTO.class);
        verify(passerelle, org.mockito.Mockito.times(2)).envoyerFichier(eq(NUMERO), eq("2250700000001"), fichiers.capture(), any());
        assertThat(fichiers.getAllValues()).extracting(FichierDTO::getNom).containsExactly("arrivage.png", "tarifs.pdf");
        verify(passerelle, never()).envoyerTexte(anyString(), anyString(), anyString());
        mvc.perform(get("/api/envois-masse/" + envoi).session(charge))
                .andExpect(jsonPath("$.statut").value("TERMINE")).andExpect(jsonPath("$.nbEchecs").value(1))
                .andExpect(jsonPath("$.piecesJointes.length()").value(2));
        mvc.perform(get("/api/envois-masse/" + envoi + "/pieces-jointes/" + pdf).session(charge)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        envoyer(charge, post("/api/envois-masse/" + envoi + "/relance-echecs"), "")
                .andExpect(jsonPath("$.statut").value("EN_COURS")).andExpect(jsonPath("$.nbEnAttente").value(1));
        envoyer(charge, post("/api/envois-masse/" + envoi + "/relance-echecs"), "").andExpect(status().isBadRequest());

        // « Envoyer maintenant » : l'echec remis en attente part aussitot, puis le bouton n'a plus d'effet
        when(passerelle.envoyerFichier(eq(NUMERO), eq("2250700000009"), any(), anyString()))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("F-2").build());
        Long attente = jdbc.queryForObject("select id from crm_message_sortant where envoi_masse_id = ? and statut = 'A_ENVOYER'", Long.class, envoi);
        envoyer(charge, post("/api/envois/S" + attente + "/immediat"), "").andExpect(jsonPath("$.statut").value("ENVOYE"));
        envoyer(charge, post("/api/envois/S" + attente + "/immediat"), "").andExpect(status().isBadRequest());
        envoyer(charge, post("/api/envois/X1/immediat"), "").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'ENVOI_IMMEDIAT' and entite_id = ?", Integer.class, attente)).isEqualTo(1);
    }

    @Test
    void lAdministrateurConsulteLesConnexionsEtLesActionsDUnCompte() throws Exception {
        Long id = jdbc.queryForObject("select id from crm_utilisateur where login = 'it-marketing'", Long.class);
        mvc.perform(post("/api/auth/connexion").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"it-marketing\",\"motDePasse\":\"faux\"}"));
        envoyer(charge, post("/api/marketing/listes"), "{\"nom\":\"Tous\",\"parCriteres\":true,\"fonctions\":[\"PRINCIPAL\"]}").andExpect(status().isOk());
        mvc.perform(get("/api/utilisateurs/" + id + "/connexions").session(admin))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].succes").value(false));
        mvc.perform(get("/api/utilisateurs/" + id + "/connexions").param("succes", "true").session(admin)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/utilisateurs/" + id + "/actions").param("action", "liste").session(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].action", containsString("LISTE")));
        mvc.perform(get("/api/utilisateurs/" + id + "/actions").session(charge)).andExpect(status().isForbidden());
    }

    @Test
    void lEnvoiEnMasseASaProprePlageEtNeDependPasDeCelleDesRelances() throws Exception {
        // Plage des relances fermee : l'envoi en masse part quand meme (sa plage est ouverte par defaut)
        jdbc.update("update crm_parametre set valeur = '0' where cle in ('whatsapp.envoi.heure_debut', 'whatsapp.envoi.heure_fin')");
        when(passerelle.envoyerTexte(eq(NUMERO), anyString(), anyString())).thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("P-1").build());
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Nuit\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andExpect(jsonPath("$.horsPlage").value(false));
        assertThat(envoiMasseService.envoyerLot()).isEqualTo(1);
        // Sa propre plage fermee : il attend et l'ecran le dit
        jdbc.update("update crm_parametre set valeur = '0' where cle in ('whatsapp.envoi_masse.heure_debut', 'whatsapp.envoi_masse.heure_fin')");
        envoyer(charge, post("/api/envois-masse"), "{\"nom\":\"Nuit 2\",\"contenu\":\"Promo\",\"numeros\":\"0700000001\"}")
                .andExpect(jsonPath("$.horsPlage").value(true)).andExpect(jsonPath("$.plageHoraire").value("0 h et 0 h"));
        assertThat(envoiMasseService.envoyerLot()).isZero();
    }

    @Test
    void uneNouvelleDiscussionPartDuNumeroDeLUtilisateurEtApparaitDansLaListe() throws Exception {
        mvc.perform(get("/api/discussions/numeros").session(charge)).andExpect(jsonPath("$[0]").value(NUMERO));
        when(passerelle.envoyerTexte(NUMERO, "2250700000007", "Bonjour Docteur"))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("N-1").build());
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"07 00 00 00 07\",\"contenu\":\"Bonjour Docteur\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu").value("Bonjour Docteur"));
        mvc.perform(get("/api/discussions").session(charge))
                .andExpect(jsonPath("$.elements[0].numeroClient").value("2250700000007"))
                .andExpect(jsonPath("$.elements[0].peutRepondre").value(true));
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"123\",\"contenu\":\"Test\"}")
                .andExpect(jsonPath("$.message", containsString("n'est pas un numéro WhatsApp valide")));

        // Modele : les variables du client se remplissent a l'envoi ; une image jointe porte le texte en legende
        mvc.perform(get("/api/discussions/modeles").session(charge)).andExpect(status().isOk());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        Long image = idDe(mvc.perform(multipart("/api/discussions/pieces-jointes").file(new MockMultipartFile("fichier", "promo.png", "image/png", png))
                .session(charge).with(csrf())));
        when(passerelle.envoyerFichier(eq(NUMERO), eq("2250700000001"), any(), eq("Bonjour Pharmacie Un (E-1)")))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("N-2").build());
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"0700000001\",\"contenu\":\"Bonjour {nom_pharmacie} ({code_client})\",\"pieceJointeIds\":[%d]}"
                .formatted(image)).andExpect(status().isOk()).andExpect(jsonPath("$.contenu").value("Bonjour Pharmacie Un (E-1)"));
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"0700000001\",\"contenu\":\"Solde {solde}\"}")
                .andExpect(jsonPath("$.message", containsString("Remplacez {solde}")));
        // L'administrateur de test n'a pas de numero relie
        jdbc.update("update crm_utilisateur set numero_whatsapp = null where login = 'admin'");
        envoyer(admin, post("/api/discussions"), "{\"numeroClient\":\"0700000007\",\"contenu\":\"Test\"}")
                .andExpect(jsonPath("$.message", containsString("Aucun numéro WhatsApp")));
    }

    @Test
    void uneReponseDepuisLAncienNumeroAHuitChiffresRejointLeFilDuNumeroADix() throws Exception {
        when(passerelle.envoyerTexte(NUMERO, "2250700000001", "Bonjour"))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("A-1").build());
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"0700000001\",\"contenu\":\"Bonjour\"}").andExpect(status().isOk());
        // Le compte WhatsApp du client garde son identifiant d'avant 2021 : 225 + 8 chiffres
        evenementWhatsappService.message(EvenementMessageDTO.builder().sessionId(NUMERO).id("A-R1").from("22500000001").name("Pharmacie Un")
                .type("TEXTE").text("Bien recu").build());
        mvc.perform(get("/api/discussions").session(charge))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].numeroClient").value("2250700000001"))
                .andExpect(jsonPath("$.elements[0].clientNom").value("Pharmacie Un"));
        mvc.perform(get("/api/discussions/messages").param("numeroAgent", NUMERO).param("numeroClient", "2250700000001").session(charge))
                .andExpect(jsonPath("$.length()").value(2));
        // Sans numero a 10 chiffres correspondant, le numero recu est garde tel quel
        evenementWhatsappService.message(EvenementMessageDTO.builder().sessionId(NUMERO).id("A-R2").from("22512345678").type("TEXTE").text("Inconnu").build());
        assertThat(jdbc.queryForObject("select numero_client from crm_message_recu where identifiant_whatsapp = 'A-R2'", String.class)).isEqualTo("22512345678");
    }

    @Test
    void unDirecteurNeVoitQueLesDiscussionsDeSonModuleSiLeParametreLeDemande() throws Exception {
        when(passerelle.envoyerTexte(NUMERO, "2250700000001", "Bonjour"))
                .thenReturn(ResultatEnvoiDTO.builder().succes(true).identifiant("D-1").build());
        envoyer(charge, post("/api/discussions"), "{\"numeroClient\":\"0700000001\",\"contenu\":\"Bonjour\"}").andExpect(status().isOk());
        creerUtilisateur("it-dir-rec", "DIR_AGENCE", "RECOUVREMENT", 1L);
        creerUtilisateur("it-dir-mkt", "DIR_AGENCE", "MARKETING", 1L);
        MockHttpSession recouvrement = connecter("it-dir-rec", MOT_DE_PASSE_ADMIN);
        MockHttpSession marketing = connecter("it-dir-mkt", MOT_DE_PASSE_ADMIN);

        // Par defaut : toutes les discussions de ses agences, quel que soit le module de l'agent
        mvc.perform(get("/api/discussions").session(recouvrement)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/discussions").session(marketing)).andExpect(jsonPath("$.total").value(1));
        jdbc.update("update crm_parametre set valeur = 'OUI' where cle = 'discussions.directeur_module_seul'");
        mvc.perform(get("/api/discussions").session(recouvrement)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/discussions/messages").param("numeroAgent", NUMERO).param("numeroClient", "2250700000001").session(recouvrement)).andExpect(status().isNotFound());
        mvc.perform(get("/api/discussions").session(marketing)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/discussions").session(admin)).andExpect(jsonPath("$.total").value(1));
    }

    private ResultActions envoyer(MockHttpSession session, MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
