package com.ubisenderpro.service;

import com.ubisenderpro.entity.RolePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests d'autorisation du service RBAC :
 * - ADMIN a toujours tous les droits (sans requête en base) ;
 * - sans rôle, aucun droit ;
 * - un rôle n'est autorisé que si la permission (menu, action) existe en base ;
 * - le remplacement des permissions d'un rôle valide, dédoublonne et ignore
 *   les entrées malformées.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {

    @Mock
    private EntityManager em;
    @Mock
    private TypedQuery<Long> countQuery;
    @Mock
    private Query updateQuery;

    @InjectMocks
    private PermissionService service;

    private static Set<String> roles(String... r) {
        return new LinkedHashSet<>(Arrays.asList(r));
    }

    /* ------------------------------- autorise ------------------------------- */

    @Test
    void autorise_adminToujoursAutorise_sansRequeteEnBase() {
        assertTrue(service.autorise(roles("ADMIN"), "settings", "MODIFIER"));
        assertTrue(service.autorise(roles("AGENT", "ADMIN"), "users", "SUPPRIMER"));
        verifyNoInteractions(em);
    }

    @Test
    void autorise_sansRole_refuse() {
        assertFalse(service.autorise(null, "clients", "VOIR"));
        assertFalse(service.autorise(Collections.emptySet(), "clients", "VOIR"));
        verifyNoInteractions(em);
    }

    @Test
    void autorise_permissionPresenteEnBase_accorde() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertTrue(service.autorise(roles("MARKETING"), "campaigns", "ENVOYER"));
    }

    @Test
    void autorise_permissionAbsenteEnBase_refuse() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        assertFalse(service.autorise(roles("AGENT"), "users", "SUPPRIMER"),
                "sans permission en base, un rôle non-admin doit être refusé");
    }

    /* ------------------------------ effectives ------------------------------ */

    @Test
    void effectives_admin_recoitToutesLesActionsDeTousLesMenus() {
        Map<String, Set<String>> droits = service.effectives(roles("ADMIN"));

        assertTrue(droits.containsKey("settings"), "ADMIN doit voir les Paramètres");
        assertTrue(droits.containsKey("users"), "ADMIN doit voir les Utilisateurs");
        assertTrue(droits.containsKey("recouvrement"), "ADMIN doit voir le Recouvrement");
        assertTrue(droits.get("settings").contains("MODIFIER"));
        assertTrue(droits.get("campaigns").contains("ENVOI_MASSE"));
        verifyNoInteractions(em);
    }

    @Test
    void effectives_sansRole_aucunDroit() {
        assertTrue(service.effectives(null).isEmpty());
        assertTrue(service.effectives(Collections.emptySet()).isEmpty());
        verifyNoInteractions(em);
    }

    /* ------------------------ definirPermissionsRole ------------------------ */

    @Test
    void definirPermissions_roleVide_estRefuse() {
        assertThrows(ValidationException.class,
                () -> service.definirPermissionsRole("  ", Collections.emptyList()));
        assertThrows(ValidationException.class,
                () -> service.definirPermissionsRole(null, Collections.emptyList()));
        verifyNoInteractions(em);
    }

    @Test
    void definirPermissions_dedoublonneEtIgnoreLesEntreesMalformees() {
        when(em.createQuery(anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);
        when(updateQuery.executeUpdate()).thenReturn(1);

        List<String> permissions = Arrays.asList(
                "clients:VOIR",
                "clients:VOIR",          // doublon -> une seule écriture
                "sans-deux-points",      // malformée -> ignorée
                null,                    // nulle -> ignorée
                "campaigns:ENVOYER");

        service.definirPermissionsRole("MARKETING", permissions);

        ArgumentCaptor<RolePermission> persistees = ArgumentCaptor.forClass(RolePermission.class);
        verify(em, times(2)).persist(persistees.capture());
        assertEquals("clients", persistees.getAllValues().get(0).getMenuCode());
        assertEquals("VOIR", persistees.getAllValues().get(0).getActionCode());
        assertEquals("campaigns", persistees.getAllValues().get(1).getMenuCode());
        assertEquals("ENVOYER", persistees.getAllValues().get(1).getActionCode());
    }

    @Test
    void definirPermissions_listeNulle_videLesDroitsSansEnRecree() {
        when(em.createQuery(anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);
        when(updateQuery.executeUpdate()).thenReturn(3);

        service.definirPermissionsRole("AGENT", null);

        verify(updateQuery).executeUpdate();
        verify(em, times(0)).persist(any());
    }
}
