package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

/** Imports clients et factures : simulation, choix des mises a jour, validation et cloisonnement. */
class ImportsIT extends IntegrationBase {

    private static final long YOPOUGON = 1L;
    private static final long VRIDI = 2L;

    @BeforeEach
    void donnees() {
        reinitialiser();
    }

    @Test
    void laSimulationClientsNeModifieRienEtLaValidationCreeLesClients() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        byte[] fichier = classeur(
                new String[] {"Code client", "Raison sociale", "Agence", "Ville", "Numéro 1", "Numéro 2"},
                new String[] {"IT-C1", "Pharmacie Alpha", "YOP", "Abidjan", "07 00 00 00 01", ""},
                new String[] {"IT-C2", "Pharmacie Beta", "Vridi", "", "+225 0500000002", "0100000003"},
                new String[] {"IT-C3", "", "YOP", "", "", ""},
                new String[] {"IT-C4", "Pharmacie Delta", "Inconnue", "", "", ""},
                new String[] {"IT-C1", "Pharmacie Alpha bis", "YOP", "", "", ""},
                new String[] {"IT-C5", "Pharmacie Epsilon", "YOP", "", "1234", ""});

        String simulation = mvc.perform(multipart("/api/imports/clients").file(fichier("clients.xlsx", fichier)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIMULE"))
                .andExpect(jsonPath("$.nbLignes").value(6))
                .andExpect(jsonPath("$.nbCreations").value(2))
                .andExpect(jsonPath("$.nbRejets").value(4))
                .andReturn().getResponse().getContentAsString();
        Long id = idDe(simulation);
        assertThat(jdbc.queryForObject("select count(*) from crm_client", Long.class)).isZero();

        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(session))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.elements[0].numeroLigne").value(4))
                .andExpect(jsonPath("$.elements[0].message").value("Raison sociale manquante."))
                .andExpect(jsonPath("$.elements[1].message", containsString("Agence inconnue")))
                .andExpect(jsonPath("$.elements[2].message", containsString("deja present ligne 2")))
                .andExpect(jsonPath("$.elements[3].message", containsString("Numero WhatsApp invalide")));

        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"))
                .andExpect(jsonPath("$.nbCrees").value(2))
                .andExpect(jsonPath("$.nbIgnores").value(4));
        assertThat(jdbc.queryForObject("select count(*) from crm_client", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select ct.numero_whatsapp from crm_contact ct join crm_client c on c.id = ct.client_id "
                + "where c.code = 'IT-C1' and ct.rang = 1", String.class)).isEqualTo("2250700000001");
        assertThat(jdbc.queryForObject("select agence_id from crm_client where code = 'IT-C2'", Long.class)).isEqualTo(VRIDI);

        // Une validation ne s'applique qu'une fois
        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cet import est deja valide."));
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'IMPORT_VALIDATION'", Long.class)).isEqualTo(1);
    }

    @Test
    void laBaseClientsUbipharmEstImporteeAvecSesColonnesDOrigine() throws Exception {
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        // Memes en-tetes que l'extraction UBIPHARM du 17/09/2026, donnees fictives
        byte[] fichier = classeur(
                new String[] {"Numérodeompte", "Agce", "Sgce", "Seg", "Nom", "Adresse1", "WhatsappMAJ", "PharmacienTitulaire",
                        "PharmacienAss.", "Chargédecde", "Comptable", "E-MailMAJ", "Commentair"},
                new String[] {"9001", "B", "B2", "P", "DU PLATEAU", "KOFFI AYA", "2250700000001", "2250700000002", "0",
                        "2250700000003", "0", "contact@exemple.ci", "0"},
                new String[] {"9002", "B", "B2", "S", "DES ROSES", "CARREFOUR", "2250700000004", "0", "0", "0", "0", "pas-un-mail", "0"},
                new String[] {"9003", "Z", "Z1", "B", "INCONNUE", "", "2250700000005", "0", "0", "0", "0", "0", "0"},
                new String[] {"9004", "B", "B2", "X", "DU LAC", "", "2250700000006", "0", "0", "0", "0", "0", "0"});

        String simulation = mvc.perform(multipart("/api/imports/clients").file(fichier("base.xlsx", fichier)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(1))
                .andExpect(jsonPath("$.nbRejets").value(3))
                .andReturn().getResponse().getContentAsString();
        Long id = idDe(simulation);
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(session))
                .andExpect(jsonPath("$.elements[0].message").value("Adresse e-mail invalide : pas-un-mail."))
                .andExpect(jsonPath("$.elements[1].message", containsString("Agence inconnue : Z")))
                .andExpect(jsonPath("$.elements[2].message", containsString("Segment inconnu : X (segments connus : P Platinum, S Silver, B Bronze)")));

        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.nbCrees").value(1));
        assertThat(jdbc.queryForObject("select concat(a.code, ':', c.sous_agence, ':', c.segmentation, ':', c.raison_sociale, ':', c.email, ':', "
                + "coalesce(c.commentaire, '-')) from crm_client c join crm_agence a on a.id = c.agence_id where c.code = '9001'", String.class))
                .isEqualTo("BKE:B2:P:DU PLATEAU:contact@exemple.ci:-");
        // Adresse1 est le nom du pharmacien titulaire
        mvc.perform(get("/api/clients").param("q", "KOFFI").session(session))
                .andExpect(jsonPath("$.elements[0].code").value("9001"))
                .andExpect(jsonPath("$.elements[0].nomPharmacien").value("KOFFI AYA"))
                .andExpect(jsonPath("$.elements[0].segmentationLibelle").value("Platinum"));
        assertThat(jdbc.queryForObject("select ct.nom from crm_contact ct join crm_client c on c.id = ct.client_id "
                + "where c.code = '9001' and ct.fonction = 'TITULAIRE'", String.class)).isEqualTo("KOFFI AYA");
        // Les cellules a 0 ne creent pas de numero
        assertThat(jdbc.queryForList("select concat(ct.rang, ':', ct.fonction, ':', ct.numero_whatsapp) from crm_contact ct "
                + "join crm_client c on c.id = ct.client_id where c.code = '9001' order by ct.id", String.class))
                .containsExactly("1:PRINCIPAL:2250700000001", "2:TITULAIRE:2250700000002", "2:CHARGE_COMMANDE:2250700000003");
    }

    @Test
    void unReimportSignaleLesEcartsEtNeMetAJourQueLesClientsChoisis() throws Exception {
        client("IT-C1", "Pharmacie Alpha", YOPOUGON, "2250700000001");
        client("IT-C2", "Pharmacie Beta", YOPOUGON, "2250700000002");
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        String csv = """
                code_client;raison_sociale;agence;numero_1
                IT-C1;Pharmacie Alpha Plus;YOP;0700000099
                IT-C2;Pharmacie Beta Plus;YOP;
                IT-C3;Pharmacie Gamma;YOP;0700000003
                IT-C1;Doublon;YOP;
                """;
        String simulation = mvc.perform(multipart("/api/imports/clients").file(fichier("clients.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .session(session).with(csrf()))
                .andExpect(jsonPath("$.nbMisesAJour").value(2))
                .andExpect(jsonPath("$.nbCreations").value(1))
                .andReturn().getResponse().getContentAsString();
        Long id = idDe(simulation);
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "MISE_A_JOUR").session(session))
                .andExpect(jsonPath("$.elements[0].message",
                        containsString("Raison sociale : Pharmacie Alpha -> Pharmacie Alpha Plus ; Numero WhatsApp : 2250700000001 -> 2250700000099")));

        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clesAMettreAJour\":[\"it-c1\"]}"))
                .andExpect(jsonPath("$.nbCrees").value(1))
                .andExpect(jsonPath("$.nbMisAJour").value(1))
                .andExpect(jsonPath("$.nbIgnores").value(2));

        assertThat(jdbc.queryForObject("select raison_sociale from crm_client where code = 'IT-C1'", String.class)).isEqualTo("Pharmacie Alpha Plus");
        assertThat(jdbc.queryForObject("select raison_sociale from crm_client where code = 'IT-C2'", String.class)).isEqualTo("Pharmacie Beta");
        // L'ancien numero est archive, pas supprime
        assertThat(jdbc.queryForList("select concat(ct.numero_whatsapp, ':', ct.statut) from crm_contact ct join crm_client c on c.id = ct.client_id "
                + "where c.code = 'IT-C1' order by ct.id", String.class))
                .containsExactly("2250700000001:ARCHIVE", "2250700000099:ACTIF");
    }

    @Test
    void unDirecteurDAgenceNImporteQueDansSonAgenceEtUnAgentNImportePas() throws Exception {
        creerUtilisateur("it-dir", "DIR_AGENCE", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", YOPOUGON);
        String csv = "code_client,raison_sociale,agence\nIT-C1,Pharmacie Alpha,YOP\nIT-C2,Pharmacie Beta,VRI\n";

        mvc.perform(multipart("/api/imports/clients").file(fichier("clients.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .session(connecter("it-dir", MOT_DE_PASSE_ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(1))
                .andExpect(jsonPath("$.nbRejets").value(1));

        mvc.perform(multipart("/api/imports/clients").file(fichier("clients.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .session(connecter("it-agent", MOT_DE_PASSE_ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());

        // Seul l'import du directeur existe : la tentative de l'agent n'a rien cree
        mvc.perform(get("/api/imports").session(connecter("admin", MOT_DE_PASSE_ADMIN))).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void unFichierSansLesColonnesObligatoiresEstRefuseEnEntier() throws Exception {
        String csv = "reference;client\nFAC-1;IT-C1\n";
        mvc.perform(multipart("/api/recouvrement/imports/factures").file(fichier("factures.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .session(connecter("admin", MOT_DE_PASSE_ADMIN)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Champs obligatoires sans colonne : Date facture, Date d'echeance, Montant, Solde")));
        assertThat(jdbc.queryForObject("select count(*) from crm_import", Long.class)).isZero();
    }

    @Test
    void lImportDeFacturesRejetteLesLignesAnormalesEtLeSoldeFaitFoi() throws Exception {
        client("F-C1", "Pharmacie Yop", YOPOUGON, null);
        client("F-C2", "Pharmacie Vridi", VRIDI, null);
        MockHttpSession session = connecter("admin", MOT_DE_PASSE_ADMIN);
        // Export Excel francais classique : point-virgule et encodage Windows-1252
        String csv = """
                N° facture;Code client;Date facture;Date d'échéance;Montant TTC;Reste à payer;Sous-type;Nature
                FAC-1;F-C1;01/09/2026;30/09/2026;150 000;100 000;Normal;Facture
                FAC-2;F-C1;01/09/2026;15/08/2026;50 000;50 000;Normal;Facture
                FAC-3;INCONNU;01/09/2026;30/09/2026;50 000;50 000;Normal;Facture
                FAC-4;F-C2;01/09/2026;30/09/2026;75 000;0;Renforcement de stock;Facture
                FAC-5;F-C1;01/09/2026;30/09/2026;10 000;10 000;Bizarre;Facture
                AV-1;F-C1;05/09/2026;05/09/2026;-20 000;-20 000;;Avoir
                FAC-6;F-C1;01/09/2026;30/09/2026;10 000;12 000;Normal;Facture
                """;
        String simulation = mvc.perform(multipart("/api/recouvrement/imports/factures")
                .file(fichier("factures.csv", csv.getBytes(Charset.forName("windows-1252")))).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(3))
                .andExpect(jsonPath("$.nbRejets").value(4))
                .andReturn().getResponse().getContentAsString();
        Long id = idDe(simulation);
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(session))
                .andExpect(jsonPath("$.elements[0].message", containsString("precede la date de facture")))
                .andExpect(jsonPath("$.elements[1].message", containsString("Client inconnu : INCONNU")))
                .andExpect(jsonPath("$.elements[2].message", containsString("Sous-type inconnu : Bizarre")))
                .andExpect(jsonPath("$.elements[3].message", containsString("depasse le montant")));

        mvc.perform(post("/api/recouvrement/imports/factures/" + id + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.nbCrees").value(3));
        assertThat(jdbc.queryForList("select concat(reference, ':', nature, ':', statut, ':', solde) from crm_facture order by reference", String.class))
                .containsExactly("AV-1:AVOIR:A_RELANCER:-20000.00", "FAC-1:FACTURE:A_RELANCER:100000.00", "FAC-4:FACTURE:SOLDEE:0.00");

        // Le client a regle FAC-1 : le nouveau solde fait foi apres validation
        String reimport = "reference;code_client;date_facture;date_echeance;montant;solde\nFAC-1;F-C1;2026-09-01;2026-09-30;150000;0\n";
        String secondeSimulation = mvc.perform(multipart("/api/recouvrement/imports/factures")
                .file(fichier("factures.csv", reimport.getBytes(StandardCharsets.UTF_8))).session(session).with(csrf()))
                .andExpect(jsonPath("$.nbMisesAJour").value(1))
                .andReturn().getResponse().getContentAsString();
        Long secondId = idDe(secondeSimulation);
        mvc.perform(get("/api/imports/" + secondId + "/lignes").session(session))
                .andExpect(jsonPath("$.elements[0].message").value("Solde : 100 000,00 -> 0,00"));
        mvc.perform(post("/api/recouvrement/imports/factures/" + secondId + "/validation").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"toutesLesMisesAJour\":true}"))
                .andExpect(jsonPath("$.nbMisAJour").value(1));
        assertThat(jdbc.queryForObject("select statut from crm_facture where reference = 'FAC-1'", String.class)).isEqualTo("SOLDEE");
    }

    @Test
    void laRechercheDeFacturesResteCloisonneeParAgence() throws Exception {
        Long c1 = client("F-C1", "Pharmacie Yop", YOPOUGON, null);
        Long c2 = client("F-C2", "Pharmacie Vridi", VRIDI, null);
        facture("FAC-1", c1, "2026-01-31", "50000.00");
        facture("FAC-2", c1, "2099-01-31", "50000.00");
        facture("FAC-3", c2, "2026-01-31", "50000.00");
        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", YOPOUGON);
        creerUtilisateur("it-marketing", "AGENT", "MARKETING", YOPOUGON);
        // Un agent ne voit que les factures des clients de son portefeuille
        jdbc.update("insert into crm_affectation (client_id, agent_id, date_debut, affecte_par) select ?, id, utc_timestamp(), id "
                + "from crm_utilisateur where login = 'it-agent'", c1);

        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/recouvrement/factures").session(agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].reference").value("FAC-1"))
                .andExpect(jsonPath("$.elements[0].joursRetard").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/recouvrement/factures").param("enRetard", "true").session(agent))
                .andExpect(jsonPath("$.elements", hasSize(1)));
        mvc.perform(get("/api/recouvrement/factures").session(connecter("admin", MOT_DE_PASSE_ADMIN)))
                .andExpect(jsonPath("$.total").value(3));
        // Sans le module Recouvrement, les factures ne sont pas accessibles
        mvc.perform(get("/api/recouvrement/factures").session(connecter("it-marketing", MOT_DE_PASSE_ADMIN)))
                .andExpect(status().isForbidden());
    }

    private Long client(String code, String raisonSociale, long agenceId, String numero) {
        jdbc.update("insert into crm_client (code, raison_sociale, agence_id, created_at) values (?, ?, ?, utc_timestamp())", code, raisonSociale, agenceId);
        Long id = jdbc.queryForObject("select id from crm_client where code = ?", Long.class, code);
        if (numero != null) {
            jdbc.update("insert into crm_contact (client_id, fonction, numero_whatsapp, rang, created_at) values (?, 'PRINCIPAL', ?, 1, utc_timestamp())", id, numero);
        }
        return id;
    }

    private void facture(String reference, Long clientId, String echeance, String solde) {
        jdbc.update("""
                insert into crm_facture (reference, client_id, sous_type_id, date_facture, date_echeance, montant, solde, created_at)
                values (?, ?, (select id from crm_sous_type_compte where code = 'NORMAL'), '2026-01-01', ?, ?, ?, utc_timestamp())
                """, reference, clientId, echeance, solde, solde);
    }

    private MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, MediaType.APPLICATION_OCTET_STREAM_VALUE, contenu);
    }

    private byte[] classeur(String[]... lignes) throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Clients");
            for (int r = 0; r < lignes.length; r++) {
                Row ligne = feuille.createRow(r);
                for (int c = 0; c < lignes[r].length; c++) {
                    ligne.createCell(c).setCellValue(lignes[r][c]);
                }
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    private Long idDe(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
