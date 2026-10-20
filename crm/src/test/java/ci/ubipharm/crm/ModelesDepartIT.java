package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Modeles de depart livres par V1.5.5 : rangés dans leur module, acceptes tels quels a la modification, exportables en Word. */
class ModelesDepartIT extends IntegrationBase {

    private static final String RECOUVREMENT = "/api/recouvrement/modeles-messages";
    private static final String MARKETING = "/api/marketing/modeles";

    @Autowired
    DataSource dataSource;

    private final JsonMapper json = new JsonMapper();
    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        // reinitialiser() vide les modeles : on rejoue la migration des modeles de depart
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1.5.5__modeles_de_base.sql")).execute(dataSource);
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void chaqueModuleNeVoitQueSesModelesDeDepart() throws Exception {
        mvc.perform(get(RECOUVREMENT).session(admin))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].nom", hasItems("Relance préventive", "Relance échue")));
        mvc.perform(get(MARKETING).session(admin))
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[*].nom", hasItems("Promo · Annonce mensuelle", "Dispo · Retour de rupture", "Info · Rappel de garde",
                        "Anniversaire client")));
        // Les sauts de ligne sont de vrais retours a la ligne, pas des "\n" litteraux
        String contenu = jdbc.queryForObject("select contenu from crm_modele_message where nom = 'Relance échue'", String.class);
        assertThat(contenu).startsWith("Bonjour Docteur *{nom_client}*,\n\n").doesNotContain("\\n");
    }

    @Test
    void chaqueModeleDeDepartPasseLaValidationEtSExporteEnWord() throws Exception {
        for (String base : List.of(RECOUVREMENT, MARKETING)) {
            List<Map<String, Object>> modeles = json.readValue(mvc.perform(get(base).session(admin)).andReturn().getResponse().getContentAsString(),
                    new TypeReference<>() { });
            for (Map<String, Object> m : modeles) {
                Map<String, Object> demande = Map.of("nom", m.get("nom"), "typeCampagne", m.get("typeCampagne"), "canal", m.get("canal"),
                        "contenu", m.get("contenu"), "typeInformation", m.get("typeInformation") == null ? "" : m.get("typeInformation"));
                mvc.perform(put(base + "/" + m.get("id")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(demande)))
                        .andExpect(status().isOk());
                byte[] word = mvc.perform(get(base + "/" + m.get("id") + "/word").session(admin))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Disposition", containsString(".docx")))
                        .andReturn().getResponse().getContentAsByteArray();
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(word)); XWPFWordExtractor texte = new XWPFWordExtractor(doc)) {
                    assertThat(texte.getText()).contains(((String) m.get("contenu")).lines().filter(l -> !l.isBlank()).findFirst().orElseThrow());
                }
            }
        }
    }
}
