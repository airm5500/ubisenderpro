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
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Non-régression : un seul contact principal par client.
 * - le premier contact d'un client devient automatiquement principal ;
 * - marquer un contact principal dé-marque les autres (bulk update).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private TypedQuery<Long> countQuery;
    @Mock
    private Query updateQuery;

    @InjectMocks
    private ContactService service;

    @Test
    void creer_premierContact_devientPrincipal() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);       // aucun contact existant
        when(em.createQuery(anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);

        ClientContact c = new ClientContact();
        c.setClientId(10L);
        c.setContactPrincipal(false);

        service.creer(c);

        assertTrue(c.isContactPrincipal(), "le 1er contact d'un client doit devenir principal");
        verify(em).persist(c);
    }

    @Test
    void creer_contactPrincipal_demarqueLesAutres() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(2L);       // 2 contacts déjà présents
        when(em.createQuery(anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);

        ClientContact c = new ClientContact();
        c.setClientId(10L);
        c.setContactPrincipal(true);

        service.creer(c);

        // Les autres contacts doivent être dé-marqués (une exécution d'UPDATE).
        verify(updateQuery, times(1)).executeUpdate();
        assertTrue(c.isContactPrincipal());
    }

    @Test
    void creer_contactNonPrincipal_avecExistants_neTouchePasAuxAutres() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(3L);       // contacts déjà présents

        ClientContact c = new ClientContact();
        c.setClientId(10L);
        c.setContactPrincipal(false);

        service.creer(c);

        assertFalse(c.isContactPrincipal(), "un contact non principal reste non principal s'il y en a déjà");
        verify(updateQuery, times(0)).executeUpdate();
    }
}
