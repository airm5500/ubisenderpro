package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

/** Modeles de messages en Word : export, retouche, reimport ; document Word quelconque ; reactivation d'un modele archive. */
class ModeleWordIT extends IntegrationBase {

    private static final String BASE = "/api/recouvrement/modeles-messages";
    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void unModeleExporteEnWordSeRetoucheEtSeReimporte() throws Exception {
        String corps = mvc.perform(post(BASE).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"nom":"Rappel J-3","typeCampagne":"AVANT_ECHEANCE","canal":"EMAIL","objet":"Échéance proche","contenu":"Bonjour {nom_pharmacie},\\nvotre échéance approche."}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(corps, "$.id")).longValue();

        byte[] word = mvc.perform(get(BASE + "/" + id + "/word").session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("rappel_j_3.docx")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] retouche;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word)); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (p.getText().equals("votre échéance approche.")) {
                    p.getRuns().getFirst().setText("votre échéance du {date_echeance} approche.", 0);
                }
            }
            doc.write(sortie);
            retouche = sortie.toByteArray();
        }
        mvc.perform(multipart(BASE + "/lecture-word").file(new MockMultipartFile("fichier", "rappel_j_3.docx", "application/octet-stream", retouche))
                        .session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modele.nom").value("Rappel J-3"))
                .andExpect(jsonPath("$.modele.typeCampagne").value("AVANT_ECHEANCE"))
                .andExpect(jsonPath("$.modele.canal").value("EMAIL"))
                .andExpect(jsonPath("$.modele.objet").value("Échéance proche"))
                .andExpect(jsonPath("$.modele.contenu").value("Bonjour {nom_pharmacie},\nvotre échéance du {date_echeance} approche."))
                .andExpect(jsonPath("$.avertissements", hasSize(0)));
        // Rien n'est enregistre : le formulaire est pre-rempli
        assertThat(jdbc.queryForObject("select count(*) from crm_modele_message where nom = 'Rappel J-3'", Integer.class)).isEqualTo(1);

        mvc.perform(post(BASE + "/" + id + "/archivage").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(post(BASE + "/" + id + "/reactivation").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ACTIF"));
        mvc.perform(post(BASE + "/" + id + "/reactivation").session(admin).with(csrf()))
                .andExpect(jsonPath("$.message").value("Ce modèle n'est pas archivé."));
    }

    @Test
    void unDocumentWordQuelconqueDevientLeTexteDuMessage() throws Exception {
        byte[] word;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Chère «Nom_Pharmacie»,");
            doc.createParagraph().createRun().setText("Votre solde est de {{solde_total}} F.");
            doc.createTable(1, 2);
            doc.write(sortie);
            word = sortie.toByteArray();
        }
        mvc.perform(multipart("/api/marketing/modeles/lecture-word").file(new MockMultipartFile("fichier", "Promo_novembre.docx", "application/octet-stream", word))
                        .session(admin).with(csrf()))
                .andExpect(jsonPath("$.modele.nom").value("Promo novembre"))
                .andExpect(jsonPath("$.modele.typeCampagne").value("PROMOTION"))
                .andExpect(jsonPath("$.modele.contenu").value("Chère {nom_pharmacie},\nVotre solde est de {solde_total} F."))
                .andExpect(jsonPath("$.avertissements[0]", containsString("tableaux")));
        mvc.perform(multipart(BASE + "/lecture-word").file(new MockMultipartFile("fichier", "modele.doc", "application/msword", new byte[] {1, 2}))
                        .session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(".docx")));
    }
}
