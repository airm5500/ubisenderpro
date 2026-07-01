package com.ubisenderpro.service;

import com.ubisenderpro.entity.ClientContact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Non-régression Lot B : le ciblage par agence/région/tournée utilise un filtre
 * {@code IN}. Une seule valeur doit produire une liste d'un élément (comportement
 * identique à l'égalité), plusieurs valeurs (CSV) une liste multi-éléments.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampagneCiblageTest {

    @Mock
    private EntityManager em;
    @Mock
    private TypedQuery<ClientContact> query;

    @InjectMocks
    private CampagneService service;

    @SuppressWarnings("unchecked")
    private List<String> capturerValeurs(String valeur) throws Exception {
        when(em.createQuery(anyString(), eq(ClientContact.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        Method m = CampagneService.class.getDeclaredMethod("contactsParChamp", String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, "agence", valeur);

        org.mockito.ArgumentCaptor<Object> cap = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(query).setParameter(eq("vals"), cap.capture());
        return (List<String>) cap.getValue();
    }

    @Test
    void uneSeuleValeur_donneListeUnElement() throws Exception {
        List<String> vals = capturerValeurs("YOP");
        assertEquals(Collections.singletonList("YOP"), vals);
    }

    @Test
    void plusieursValeursCsv_donneListeMulti() throws Exception {
        List<String> vals = capturerValeurs("YOP, COCODY , PLATEAU");
        assertEquals(java.util.Arrays.asList("YOP", "COCODY", "PLATEAU"), vals);
    }
}
