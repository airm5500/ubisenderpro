package ci.ubipharm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

/** Region, ville, commune et tournee choisies dans des listes ; badges des segments. */
class ReferentielIT extends IntegrationBase {

    private MockHttpSession admin;

    @BeforeEach
    void donnees() throws Exception {
        reinitialiser();
        admin = connecter("admin", MOT_DE_PASSE_ADMIN);
    }

    @Test
    void laCommuneDonneSaVilleEtSaRegionEtLaTourneeSuitLAgence() throws Exception {
        Long tournee = idDe(envoyer(post("/api/referentiels"), "{\"type\":\"TOURNEE\",\"code\":\"YN\",\"libelle\":\"IT Yopougon Nord\",\"agenceId\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.agenceLibelle").value("Yopougon")));
        Long tourneeVridi = idDe(envoyer(post("/api/referentiels"), "{\"type\":\"TOURNEE\",\"libelle\":\"IT Port\",\"agenceId\":2}"));

        envoyer(post("/api/clients"), """
                {"code":"R-1","raisonSociale":"Pharmacie Cocody","agenceId":1,"communeId":%d,"tourneeId":%d,"segmentation":"P",
                 "dateAnniversaire":"1975-03-15"}""".formatted(referentiel("COMMUNE", "Cocody"), tournee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ville").value("Abidjan"))
                .andExpect(jsonPath("$.region").value("District autonome d'Abidjan"))
                .andExpect(jsonPath("$.commune").value("Cocody"))
                .andExpect(jsonPath("$.tournee").value("IT Yopougon Nord"))
                .andExpect(jsonPath("$.dateAnniversaire").value("1975-03-15"))
                .andExpect(jsonPath("$.segmentationBadge").value("COURONNE"));

        envoyer(post("/api/clients"), """
                {"code":"R-2","raisonSociale":"Pharmacie Mélangée","agenceId":1,"villeId":%d,"communeId":%d,"tourneeId":%d}"""
                .formatted(referentiel("VILLE", "Bouaké"), referentiel("COMMUNE", "Cocody"), tourneeVridi))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("La commune Cocody n'est pas dans la ville Bouaké.")))
                .andExpect(jsonPath("$.message", containsString("La tournée IT Port est desservie par l'agence Vridi.")));

        // Une ville deplacee dans une autre region entraine ses clients
        Long itRegion = idDe(envoyer(post("/api/referentiels"), "{\"type\":\"REGION\",\"libelle\":\"IT Région\"}"));
        envoyer(put("/api/referentiels/" + referentiel("VILLE", "Abidjan")), "{\"type\":\"VILLE\",\"libelle\":\"Abidjan\",\"parentId\":" + itRegion + "}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select region_id from crm_client where code = 'R-1'", Long.class)).isEqualTo(itRegion);
        envoyer(put("/api/referentiels/" + referentiel("VILLE", "Abidjan")),
                "{\"type\":\"VILLE\",\"libelle\":\"Abidjan\",\"parentId\":" + referentiel("REGION", "District autonome d'Abidjan") + "}");
    }

    @Test
    void lesListesSeTiennentSansDoublonEtUneEntreeArchiveeNeSeChoisitPlus() throws Exception {
        Long ville = idDe(envoyer(post("/api/referentiels"), "{\"type\":\"VILLE\",\"libelle\":\"IT Ville\",\"parentId\":" + referentiel("REGION", "Gôh") + "}")
                .andExpect(jsonPath("$.parentLibelle").value("Gôh")));
        envoyer(post("/api/referentiels"), "{\"type\":\"VILLE\",\"libelle\":\"it ville\",\"parentId\":" + referentiel("REGION", "Gôh") + "}")
                .andExpect(status().isBadRequest());
        envoyer(post("/api/referentiels"), "{\"type\":\"COMMUNE\",\"libelle\":\"IT Commune\"}")
                .andExpect(jsonPath("$.message").value("Choisissez la ville de la commune."));
        envoyer(post("/api/referentiels"), "{\"type\":\"PAYS\",\"libelle\":\"IT Pays\"}").andExpect(status().isBadRequest());

        mvc.perform(post("/api/referentiels/" + ville + "/archivage").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(get("/api/referentiels").param("type", "VILLE").session(admin))
                .andExpect(jsonPath("$[?(@.libelle == 'IT Ville')]").isEmpty());
        envoyer(post("/api/clients"), "{\"code\":\"R-3\",\"raisonSociale\":\"Pharmacie X\",\"agenceId\":1,\"villeId\":" + ville + "}")
                .andExpect(jsonPath("$.message").value("Ville archivée : IT Ville."));
        assertThat(jdbc.queryForObject("select count(*) from crm_journal_action where action like 'REFERENTIEL_%'", Integer.class)).isEqualTo(2);

        creerUtilisateur("it-agent", "AGENT", "RECOUVREMENT", 1L);
        MockHttpSession agent = connecter("it-agent", MOT_DE_PASSE_ADMIN);
        mvc.perform(get("/api/referentiels").param("type", "COMMUNE").session(agent)).andExpect(status().isOk());
        mvc.perform(post("/api/referentiels").session(agent).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"REGION\",\"libelle\":\"IT Interdite\"}")).andExpect(status().isForbidden());
    }

    @Test
    void chaqueSegmentPorteUnBadgeChoisiDansLeJeu() throws Exception {
        envoyer(post("/api/segments"), "{\"code\":\"it-or\",\"libelle\":\"Or\",\"badge\":\"LICORNE\"}")
                .andExpect(jsonPath("$.message").value("Choisissez un badge parmi ceux proposés."));
        Long or = idDe(envoyer(post("/api/segments"), "{\"code\":\"it-or\",\"libelle\":\"Or\",\"badge\":\"TROPHEE\"}")
                .andExpect(jsonPath("$.code").value("IT-OR")).andExpect(jsonPath("$.ordre").value(4)));
        envoyer(post("/api/segments"), "{\"code\":\"P\",\"libelle\":\"Autre\",\"badge\":\"ETOILE\"}").andExpect(status().isBadRequest());
        envoyer(put("/api/segments/" + or), "{\"libelle\":\"Or\",\"badge\":\"SOLEIL\"}").andExpect(jsonPath("$.badge").value("SOLEIL"));

        mvc.perform(get("/api/clients/segments").session(admin))
                .andExpect(jsonPath("$[0].badge").value("COURONNE"))
                .andExpect(jsonPath("$[3].badge").value("SOLEIL"));
        mvc.perform(post("/api/segments/" + or + "/archivage").session(admin).with(csrf())).andExpect(jsonPath("$.statut").value("ARCHIVE"));
        mvc.perform(get("/api/clients/segments").session(admin)).andExpect(jsonPath("$[3]").doesNotExist());
    }

    @Test
    void lImportControleLaLocalisationContreLesReferentiels() throws Exception {
        String csv = """
                Code client;Nom;Agence;Ville;Commune;Région;Anniversaire
                R-10;PHARMACIE A;Y;bouake;;;15/03/1980
                R-11;PHARMACIE B;Y;Abidjan;Plateau;;
                R-12;PHARMACIE C;Y;Paris;;;
                R-13;PHARMACIE D;Y;Bouaké;Cocody;;
                R-14;PHARMACIE E;Y;Gagnoa;;Poro;31/02/1990
                """;
        String simulation = mvc.perform(multipart("/api/imports/clients")
                        .file(new MockMultipartFile("fichier", "clients.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))).session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbCreations").value(2))
                .andExpect(jsonPath("$.nbRejets").value(3))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(simulation, "$.id")).longValue();
        mvc.perform(get("/api/imports/" + id + "/lignes").param("action", "REJET").session(admin))
                .andExpect(jsonPath("$.elements[0].message").value("Ville inconnue du référentiel : Paris (à ajouter dans Paramètres, Référentiels)."))
                .andExpect(jsonPath("$.elements[1].message").value("La commune Cocody n'est pas dans la ville Bouaké."))
                .andExpect(jsonPath("$.elements[2].message", containsString("La ville Gagnoa n'est pas dans la région Poro.")))
                .andExpect(jsonPath("$.elements[2].message", containsString("Date d'anniversaire invalide : 31/02/1990")));

        mvc.perform(post("/api/imports/clients/" + id + "/validation").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.nbCrees").value(2));
        assertThat(jdbc.queryForObject("""
                select concat(v.libelle, '/', r.libelle, '/', c.date_anniversaire) from crm_client c
                join crm_referentiel v on v.id = c.ville_id join crm_referentiel r on r.id = c.region_id where c.code = 'R-10'
                """, String.class)).isEqualTo("Bouaké/Gbêkê/1980-03-15");
        assertThat(jdbc.queryForObject("select co.libelle from crm_client c join crm_referentiel co on co.id = c.commune_id where c.code = 'R-11'",
                String.class)).isEqualTo("Plateau");
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, String json) throws Exception {
        return mvc.perform(requete.session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long idDe(ResultActions resultat) throws Exception {
        return ((Number) JsonPath.read(resultat.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
