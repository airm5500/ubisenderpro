package com.ubisenderpro.service;

import com.ubisenderpro.entity.ReferentielGeo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Non-régression : la modification d'un référentiel doit bien persister le
 * nouveau code et le nouveau libellé (bug « la ré-édition réaffiche l'ancien
 * code » — la garantie serveur ne doit jamais régresser).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferentielGeoServiceTest {

    @Mock
    private EntityManager em;

    @InjectMocks
    private ReferentielGeoService service;

    private ReferentielGeo existant(Long id, String code, String libelle) {
        ReferentielGeo r = new ReferentielGeo();
        r.setId(id);
        r.setType("PAYS");
        r.setCode(code);
        r.setLibelle(libelle);
        r.setActif(true);
        return r;
    }

    @Test
    void modifier_metAJourCodeEtLibelle() {
        ReferentielGeo ex = existant(1L, "PAYS-DEFAUT", "Côte d'Ivoire");
        when(em.find(eq(ReferentielGeo.class), eq(1L))).thenReturn(ex);
        when(em.merge(any())).then(returnsFirstArg());

        ReferentielGeo data = new ReferentielGeo();
        data.setCode("CI");
        data.setLibelle("Côte d'Ivoire");

        ReferentielGeo resultat = service.modifier(1L, data);

        assertEquals("CI", resultat.getCode(), "le nouveau code doit être persisté");
        assertEquals("CI", ex.getCode(), "l'entité gérée doit porter le nouveau code");
    }

    @Test
    void modifier_codeVide_conserveAncienCode() {
        ReferentielGeo ex = existant(2L, "REG-2026-0001", "Abidjan");
        when(em.find(eq(ReferentielGeo.class), eq(2L))).thenReturn(ex);
        when(em.merge(any())).then(returnsFirstArg());

        ReferentielGeo data = new ReferentielGeo();
        data.setCode("   ");            // vide -> ne doit pas écraser
        data.setLibelle("Abidjan Sud");

        ReferentielGeo resultat = service.modifier(2L, data);

        assertEquals("REG-2026-0001", resultat.getCode(), "un code vide ne doit pas écraser l'existant");
        assertEquals("Abidjan Sud", resultat.getLibelle(), "le libellé doit être mis à jour");
    }
}
