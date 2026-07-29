package com.ubisenderpro.service;

import com.ubisenderpro.entity.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Mise a jour selective des comptes clients (onglet dedie) : seuls les champs
 * explicitement demandes sont modifies, le reste de la fiche est intact.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MajSelectiveTest {

    @Mock
    private EntityManager em;
    @Mock
    private ReferentielGeoService referentielGeoService;

    @InjectMocks
    private ClientService service;

    private Client client(long id, String agence, String tournee) {
        Client c = new Client();
        c.setId(id);
        c.setNumeroClient("C-" + id);
        c.setNomCompte("PHARMACIE " + id);
        c.setAgence(agence);
        c.setTournee(tournee);
        c.setSegmentationId(7L);
        return c;
    }

    @Test
    void auMoinsUnCompte() {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("agence", "ABIDJAN");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.majSelective(Collections.emptyList(), champs));
        assertEquals("ids", ex.getChamp());
    }

    @Test
    void auMoinsUnChamp() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.majSelective(Arrays.asList(1L), new LinkedHashMap<>()));
        assertEquals("champs", ex.getChamp());
    }

    /** Un champ inconnu ne suffit pas : rien ne serait modifie. */
    @Test
    void champInconnuRefuse() {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("nomCompte", "PIRATE");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.majSelective(Arrays.asList(1L), champs));
        assertEquals("champs", ex.getChamp());
    }

    @Test
    void seulLeChampDemandeChange() {
        Client c1 = client(1, "YAMOUSSOUKRO", "T1");
        when(em.find(Client.class, 1L)).thenReturn(c1);

        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("agence", "ABIDJAN");
        int n = service.majSelective(Arrays.asList(1L), champs);

        assertEquals(1, n);
        assertEquals("ABIDJAN", c1.getAgence());
        // Les autres champs ne bougent pas.
        assertEquals("T1", c1.getTournee());
        assertEquals(Long.valueOf(7L), c1.getSegmentationId());
    }

    /** Champ coche mais laisse vide : effacement explicite. */
    @Test
    void champDemandeVideEfface() {
        Client c1 = client(1, "YAMOUSSOUKRO", "T1");
        when(em.find(Client.class, 1L)).thenReturn(c1);

        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("tournee", "");
        service.majSelective(Arrays.asList(1L), champs);

        assertNull(c1.getTournee());
        assertEquals("YAMOUSSOUKRO", c1.getAgence());
    }

    @Test
    void segmentationAppliqueeATousLesComptesCoches() {
        Client c1 = client(1, "A", "T1");
        Client c2 = client(2, "B", "T2");
        when(em.find(Client.class, 1L)).thenReturn(c1);
        when(em.find(Client.class, 2L)).thenReturn(c2);

        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("segmentationId", "12");
        int n = service.majSelective(Arrays.asList(1L, 2L), champs);

        assertEquals(2, n);
        assertEquals(Long.valueOf(12L), c1.getSegmentationId());
        assertEquals(Long.valueOf(12L), c2.getSegmentationId());
        assertEquals("A", c1.getAgence());
    }

    /** Un identifiant disparu entre l'affichage et l'application n'interrompt pas le lot. */
    @Test
    void compteIntrouvableIgnore() {
        Client c2 = client(2, "B", "T2");
        when(em.find(Client.class, 1L)).thenReturn(null);
        when(em.find(Client.class, 2L)).thenReturn(c2);

        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("region", "SUD-COMOE");
        int n = service.majSelective(Arrays.asList(1L, 2L), champs);

        assertEquals(1, n);
        assertEquals("SUD-COMOE", c2.getRegion());
    }
}
