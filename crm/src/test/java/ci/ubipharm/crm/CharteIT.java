package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import ci.ubipharm.crm.rest.service.ArchivageService;

/** Parametres de l'entreprise : logo et icone, liens, mode d'envoi par defaut, archivage des documents. */
class CharteIT extends IntegrationBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private ArchivageService archivageService;

    private MockHttpSession admin;
    private List<Map<String, Object>> sauvegarde;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        sauvegarde = jdbc.queryForList("select cle, valeur from crm_parametre");
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @AfterEach
    void restaurer() {
        sauvegarde.forEach(p -> jdbc.update("update crm_parametre set valeur = ?, modifie_par = null where cle = ?", p.get("valeur"), p.get("cle")));
    }

    @Test
    void leLogoEtLIconeSeChoisissentEtSeLisentSansConnexion() throws Exception {
        mvc.perform(get("/api/charte")).andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale").value("#05253D"))
                .andExpect(jsonPath("$.logoVersion").isEmpty())
                .andExpect(jsonPath("$.canalDefaut").value("WHATSAPP"));
        mvc.perform(get("/api/charte/logo")).andExpect(status().isFound()).andExpect(header().string("Location", "/logo.svg"));

        mvc.perform(multipart("/api/charte/logo").file(new MockMultipartFile("fichier", "logo-ubipharm.png", "image/png", PNG)).session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoNom").value("logo-ubipharm.png"))
                .andExpect(jsonPath("$.logoVersion").isNumber());
        mvc.perform(get("/api/charte/logo")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));

        // Un second depot remplace le premier, garde desactive
        mvc.perform(multipart("/api/charte/logo").file(new MockMultipartFile("fichier", "v2.png", "image/png", PNG)).session(admin).with(csrf()))
                .andExpect(jsonPath("$.logoNom").value("v2.png"));
        assertThat(jdbc.queryForObject("select count(*) from crm_charte_fichier where code = 'LOGO'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from crm_charte_fichier where code = 'LOGO' and actif", Integer.class)).isEqualTo(1);

        // L'icone accepte le format .ico ; un SVG avec script est refuse
        mvc.perform(multipart("/api/charte/icone").file(new MockMultipartFile("fichier", "favicon.ico", "image/x-icon", new byte[] {0, 0, 1, 0, 1, 0}))
                .session(admin).with(csrf())).andExpect(jsonPath("$.iconeNom").value("favicon.ico"));
        mvc.perform(multipart("/api/charte/logo").file(new MockMultipartFile("fichier", "x.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8))).session(admin).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/charte/logo").file(new MockMultipartFile("fichier", "x.txt", "text/plain", "bonjour".getBytes(StandardCharsets.UTF_8)))
                .session(admin).with(csrf()))
                .andExpect(jsonPath("$.message").value("Le logo doit être une image PNG, JPEG ou SVG."));

        mvc.perform(post("/api/charte/logo/reinitialisation").session(admin).with(csrf())).andExpect(jsonPath("$.logoVersion").isEmpty());
        mvc.perform(get("/api/charte/logo")).andExpect(status().isFound());
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action = 'CHARTE_MODIFICATION'", Integer.class)).isEqualTo(4);

        creerUtilisateur("it-dr", "DIR_RECOUVREMENT", "RECOUVREMENT", 1L);
        mvc.perform(multipart("/api/charte/logo").file(new MockMultipartFile("fichier", "l.png", "image/png", PNG))
                .session(connecter("it-dr", MOT_DE_PASSE_ADMIN)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void lesNouveauxParametresSontControles() throws Exception {
        modifier("[{\"cle\":\"entreprise.site_web\",\"valeur\":\"ubipharm\"}]")
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("lien complet attendu, par exemple https://www.ubipharm.ci.")));
        modifier("[{\"cle\":\"envoi.canal_defaut\",\"valeur\":\"FAX\"}]").andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("choix inconnu.")));
        modifier("[{\"cle\":\"archivage.actif\",\"valeur\":\"peut-etre\"}]").andExpect(status().isBadRequest());
        modifier("[{\"cle\":\"archivage.repertoire\",\"valeur\":\"archives\"}]").andExpect(status().isBadRequest());

        modifier("[{\"cle\":\"entreprise.site_web\",\"valeur\":\"https://www.ubipharm.ci\"},{\"cle\":\"envoi.canal_defaut\",\"valeur\":\"EMAIL\"},"
                + "{\"cle\":\"whatsapp.mode_envoi_defaut\",\"valeur\":\"META\"},{\"cle\":\"archivage.repertoire\",\"valeur\":\"D:\\\\ARCHIVAGES\\\\\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cle == 'envoi.canal_defaut')].options").value("WHATSAPP=WhatsApp|EMAIL=E-mail|SMS=SMS"));
        assertThat(jdbc.queryForObject("select valeur from crm_parametre where cle = 'archivage.repertoire'", String.class)).isEqualTo("D:\\ARCHIVAGES");
        mvc.perform(get("/api/charte")).andExpect(jsonPath("$.siteWeb").value("https://www.ubipharm.ci"))
                .andExpect(jsonPath("$.canalDefaut").value("EMAIL")).andExpect(jsonPath("$.modeEnvoiDefaut").value("META"));
        mvc.perform(get("/api/recouvrement/variables").session(admin))
                .andExpect(jsonPath("$[?(@.code == 'lien_commande')].source").value("calcul.lien_commande"));
    }

    @Test
    void lesDocumentsSontCopiesDansLeRepertoireDArchivage() throws Exception {
        Path dossier = Files.createTempDirectory("archives");
        jdbc.update("update crm_parametre set valeur = ? where cle = 'archivage.repertoire'", dossier.toString());
        jdbc.update("update crm_parametre set valeur = 'TYPE' where cle = 'archivage.classement'");
        archivageService.archiver("rejets", "rejets_import_1.csv", "a;b".getBytes(StandardCharsets.UTF_8));
        try (var fichiers = Files.list(dossier.resolve("rejets"))) {
            assertThat(fichiers.map(f -> f.getFileName().toString()).toList()).singleElement().asString().endsWith("_rejets_import_1.csv");
        }

        // Desactive : rien n'est copie ; un repertoire invalide n'empeche jamais l'action
        jdbc.update("update crm_parametre set valeur = 'NON' where cle = 'archivage.actif'");
        archivageService.archiver("affiches", "a.png", PNG);
        assertThat(Files.exists(dossier.resolve("affiches"))).isFalse();
        jdbc.update("update crm_parametre set valeur = 'OUI' where cle = 'archivage.actif'");
        jdbc.update("update crm_parametre set valeur = 'relatif' where cle = 'archivage.repertoire'");
        archivageService.archiver("affiches", "a.png", PNG);
        assertThat(Files.exists(Path.of("relatif"))).isFalse();
    }

    private ResultActions modifier(String json) throws Exception {
        return mvc.perform(put("/api/parametres").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
