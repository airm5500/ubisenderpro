package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

/** Assistant d'import : apercu et correspondance, modeles, exemplaire, correction de lignes, rejets. */
class AssistantImportIT extends IntegrationBase {

    private static final String BASE_UBIPHARM = """
            Numérodeompte;Agce;Sgce;Seg;Nom;Adresse1;WhatsappMAJ;PharmacienTitulaire;PharmacienAss.;Chargédecde;Comptable;E-MailMAJ;Commentair
            9001;Y;Y2;P;DU PLATEAU;KOFFI AYA;2250700000001;2250700000002;0;0;0;0;0
            9002;V;V1;S;DES ROSES;KONE ALI;07 00 00 1;0;0;0;0;0;0
            9003;G;G1;B;DU LAC;TRAORE ADAMA;2250700000003;0;0;0;0;0;0
            """;

    private MockHttpSession session;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        jdbc.update("delete from crm_import_modele");
        session = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void lApercuMontreLesColonnesEtProposeLaCorrespondance() throws Exception {
        mvc.perform(multipart("/api/imports/clients/apercu").file(fichier("base.csv", BASE_UBIPHARM)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbLignes").value(3))
                .andExpect(jsonPath("$.colonnes[0].cle").value("numerodeompte"))
                .andExpect(jsonPath("$.colonnes[0].libelle").value("Numérodeompte"))
                .andExpect(jsonPath("$.colonnes[0].exemples", contains("9001", "9002", "9003")))
                .andExpect(jsonPath("$.correspondance.code_client").value("numerodeompte"))
                .andExpect(jsonPath("$.correspondance.agence").value("agce"))
                .andExpect(jsonPath("$.correspondance.nom_pharmacien").value("adresse1"))
                .andExpect(jsonPath("$.correspondance.numero_whatsapp").value("whatsappmaj"))
                .andExpect(jsonPath("$.correspondance.numero_charge_commande").value("chargedecde"))
                .andExpect(jsonPath("$.champsManquants", empty()));
    }

    @Test
    void uneCorrespondanceChoisieRemplaceLesIntitulesInconnus() throws Exception {
        String csv = "Col A;Col B;Col C;Col D\n9101;PHARMACIE A;Yopougon;0700000011\n";
        mvc.perform(multipart("/api/imports/clients").file(fichier("f.csv", csv)).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Champs obligatoires sans colonne : Numero de compte, Nom de la pharmacie, Agence")));

        String parametres = """
                {"correspondance":{"code_client":"col_a","raison_sociale":"col_b","agence":"col_c","numero_whatsapp":"col_d"}}""";
        mvc.perform(multipart("/api/imports/clients").file(fichier("f.csv", csv)).file(parametres(parametres)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(1));
    }

    @Test
    void unModeleEnregistreSertAuxImportsSuivantsPuisSArchive() throws Exception {
        String modele = mvc.perform(post("/api/imports/clients/modeles").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Export comptable\",\"correspondance\":{\"code_client\":\"col_a\",\"raison_sociale\":\"col_b\",\"agence\":\"col_c\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CLIENTS"))
                .andExpect(jsonPath("$.auteur").value("Administrateur"))
                .andReturn().getResponse().getContentAsString();
        Long modeleId = ((Number) JsonPath.read(modele, "$.id")).longValue();
        mvc.perform(get("/api/imports/clients/modeles").session(session)).andExpect(jsonPath("$", hasSize(1)));

        String csv = "Col A;Col B;Col C\n9101;PHARMACIE A;V\n";
        mvc.perform(multipart("/api/imports/clients/apercu").file(fichier("f.csv", csv)).param("modeleId", modeleId.toString())
                        .session(session).with(csrf()))
                .andExpect(jsonPath("$.correspondance.code_client").value("col_a"))
                .andExpect(jsonPath("$.champsManquants", empty()));
        mvc.perform(multipart("/api/imports/clients").file(fichier("f.csv", csv)).file(parametres("{\"modeleId\":" + modeleId + "}"))
                        .session(session).with(csrf()))
                .andExpect(jsonPath("$.nbCreations").value(1));

        mvc.perform(post("/api/imports/modeles/" + modeleId + "/archivage").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/imports/clients/modeles").session(session)).andExpect(jsonPath("$", empty()));
        // Archive, pas supprime
        assertThat(jdbc.queryForObject("select statut from crm_import_modele where id = ?", String.class, modeleId)).isEqualTo("ARCHIVE");
    }

    @Test
    void uneLigneRejeteeSeCorrigeDansLAssistantEtLesRejetsSeTelechargent() throws Exception {
        String simulation = mvc.perform(multipart("/api/imports/clients").file(fichier("base.csv", BASE_UBIPHARM)).session(session).with(csrf()))
                .andExpect(jsonPath("$.nbCreations").value(2))
                .andExpect(jsonPath("$.nbRejets").value(1))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(simulation, "$.id")).longValue();

        String rejets = mvc.perform(get("/api/imports/" + id + "/rejets").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("rejets_import_" + id + ".csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(rejets).startsWith("﻿Ligne;Numero de compte;Nom de la pharmacie;Agence;")
                .contains("3;9002;DES ROSES;V;V1;S;KONE ALI;")
                .contains("Numero WhatsApp invalide : 07 00 00 1");

        String lignes = mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(session))
                .andReturn().getResponse().getContentAsString();
        Long ligneId = ((Number) JsonPath.read(lignes, "$.elements[0].id")).longValue();
        mvc.perform(put("/api/imports/" + id + "/lignes/" + ligneId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeurs\":{\"numero_whatsapp\":\"07 00 00 00 09\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(3))
                .andExpect(jsonPath("$.nbRejets").value(0));

        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.nbCrees").value(3));
        assertThat(jdbc.queryForList("select concat(c.code, ':', a.libelle) from crm_client c join crm_agence a on a.id = c.agence_id order by c.code",
                String.class)).containsExactly("9001:Yopougon", "9002:Vridi", "9003:Gagnoa");
        assertThat(jdbc.queryForObject("select ct.numero_whatsapp from crm_contact ct join crm_client c on c.id = ct.client_id "
                + "where c.code = '9002' and ct.fonction = 'PRINCIPAL'", String.class)).isEqualTo("2250700000009");

        // Une fois validee, la simulation ne se corrige plus
        mvc.perform(put("/api/imports/" + id + "/lignes/" + ligneId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeurs\":{\"ville\":\"Abidjan\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lExemplaireSeRemplitEtSeReimporteTelQuel() throws Exception {
        String exemplaire = mvc.perform(get("/api/imports/clients/exemplaire").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(exemplaire).startsWith("﻿Numero de compte *;Nom de la pharmacie *;Agence *;Sous-agence;Segment;Nom du pharmacien;");

        mvc.perform(multipart("/api/imports/clients").file(new MockMultipartFile("fichier", "exemplaire.csv", "text/csv",
                        exemplaire.getBytes(StandardCharsets.UTF_8))).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(1));

        mvc.perform(get("/api/imports/factures/champs").session(session))
                .andExpect(jsonPath("$[0].code").value("reference"))
                .andExpect(jsonPath("$[0].obligatoire").value(true))
                .andExpect(jsonPath("$[0].alias").doesNotExist());
    }

    @Test
    void lAssistantDesFacturesExigeLeModuleRecouvrement() throws Exception {
        creerUtilisateur("it-dir-marketing", "DIR_AGENCE", "MARKETING", 1L);
        MockHttpSession directeur = connecter("it-dir-marketing", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/imports/factures/champs").session(directeur)).andExpect(status().isForbidden());
        mvc.perform(get("/api/imports/clients/champs").session(directeur)).andExpect(status().isOk());
        mvc.perform(get("/api/imports/inconnu/champs").session(directeur)).andExpect(status().isNotFound());
    }

    private MockMultipartFile fichier(String nom, String contenu) {
        return new MockMultipartFile("fichier", nom, "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile parametres(String json) {
        return new MockMultipartFile("parametres", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }
}
