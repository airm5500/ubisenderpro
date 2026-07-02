package com.ubisenderpro.security;

import com.ubisenderpro.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests d'autorisation du filtre JAX-RS : session obligatoire (401), restriction
 * par rôle (403), contrôle fin par permission menu/action (403) et déduction de
 * l'action d'après la méthode HTTP et le chemin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationFilterTest {

    @Mock
    private ResourceInfo resourceInfo;
    @Mock
    private SessionStore sessionStore;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ContainerRequestContext requete;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private AuthenticationFilter filtre;

    /* ---------- Ressources factices portant les annotations à tester ---------- */

    static class RessourceAdminSurMethode {
        @Secured(roles = {"ADMIN"})
        public void action() { }
    }

    @Secured(roles = {"ADMIN"})
    static class RessourceAdminSurClasse {
        public void action() { }
    }

    static class RessourceEnvoiCampagne {
        @Secured(menu = "campaigns", action = "ENVOYER")
        public void envoyer() { }
    }

    static class RessourceClients {
        @Secured(menu = "clients")
        public void auto() { }
    }

    static class RessourceSessionSeule {
        @Secured
        public void libre() { }
    }

    /* --------------------------------- Aides --------------------------------- */

    private static AuthenticatedUser utilisateur(String... roles) {
        return new AuthenticatedUser(1L, "test", "Utilisateur Test",
                new LinkedHashSet<>(Arrays.asList(roles)));
    }

    private void cibler(Class<?> classe, String methode) throws Exception {
        when(resourceInfo.getResourceMethod()).thenReturn(classe.getDeclaredMethod(methode));
        doReturn(classe).when(resourceInfo).getResourceClass();
    }

    private void connecter(AuthenticatedUser u) {
        when(requete.getHeaderString("Authorization")).thenReturn("Bearer jeton-valide");
        when(sessionStore.validate("jeton-valide")).thenReturn(u);
        when(requete.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("clients");
        when(requete.getMethod()).thenReturn("GET");
    }

    private int statutRefus() {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requete).abortWith(captor.capture());
        return captor.getValue().getStatus();
    }

    /* ------------------------- Session (401 attendus) ------------------------- */

    @Test
    void sansEnTeteAuthorization_renvoie401() throws Exception {
        cibler(RessourceSessionSeule.class, "libre");
        when(requete.getHeaderString("Authorization")).thenReturn(null);

        filtre.filter(requete);

        assertEquals(401, statutRefus(), "sans jeton, la requête doit être refusée en 401");
        verify(requete, never()).setSecurityContext(any());
    }

    @Test
    void jetonInvalideOuExpire_renvoie401() throws Exception {
        cibler(RessourceSessionSeule.class, "libre");
        when(requete.getHeaderString("Authorization")).thenReturn("Bearer perime");
        when(sessionStore.validate("perime")).thenReturn(null);

        filtre.filter(requete);

        assertEquals(401, statutRefus());
        verify(requete, never()).setSecurityContext(any());
    }

    /* ------------------------- Rôles (403 attendus) ------------------------- */

    @Test
    void roleRequisAbsent_renvoie403() throws Exception {
        cibler(RessourceAdminSurMethode.class, "action");
        connecter(utilisateur("AGENT"));

        filtre.filter(requete);

        assertEquals(403, statutRefus(), "un AGENT ne doit pas accéder à un endpoint ADMIN");
        verify(requete, never()).setSecurityContext(any());
    }

    @Test
    void restrictionDeRoleSurLaClasse_appliqueeAussi() throws Exception {
        cibler(RessourceAdminSurClasse.class, "action");
        connecter(utilisateur("MARKETING"));

        filtre.filter(requete);

        assertEquals(403, statutRefus(), "l'annotation de classe doit s'appliquer aux méthodes non annotées");
    }

    @Test
    void roleRequisPresent_laisseLaRequetePasser() throws Exception {
        cibler(RessourceAdminSurMethode.class, "action");
        connecter(utilisateur("ADMIN"));

        filtre.filter(requete);

        verify(requete, never()).abortWith(any());
        verify(requete).setSecurityContext(any(SecurityContext.class));
    }

    /* --------------------- Permissions fines (menu/action) --------------------- */

    @Test
    void permissionRefusee_renvoie403() throws Exception {
        cibler(RessourceEnvoiCampagne.class, "envoyer");
        connecter(utilisateur("MARKETING"));
        when(permissionService.autorise(anyCollection(), eq("campaigns"), eq("ENVOYER")))
                .thenReturn(false);

        filtre.filter(requete);

        assertEquals(403, statutRefus(), "sans la permission campaigns:ENVOYER, l'appel doit être refusé");
        verify(requete, never()).setSecurityContext(any());
    }

    @Test
    void permissionAccordee_laisseLaRequetePasser() throws Exception {
        cibler(RessourceEnvoiCampagne.class, "envoyer");
        connecter(utilisateur("MARKETING"));
        when(permissionService.autorise(anyCollection(), eq("campaigns"), eq("ENVOYER")))
                .thenReturn(true);

        filtre.filter(requete);

        verify(requete, never()).abortWith(any());
        verify(requete).setSecurityContext(any(SecurityContext.class));
    }

    @Test
    void sessionValideSansRestriction_passeSansControleDePermission() throws Exception {
        cibler(RessourceSessionSeule.class, "libre");
        connecter(utilisateur("LECTURE"));

        filtre.filter(requete);

        verify(requete, never()).abortWith(any());
        verify(requete).setSecurityContext(any(SecurityContext.class));
        verifyNoInteractions(permissionService);
    }

    /* ---------- Déduction de l'action quand @Secured(menu=…) sans action ---------- */

    private String actionDeduitePour(String methodeHttp, String chemin) throws Exception {
        cibler(RessourceClients.class, "auto");
        connecter(utilisateur("AGENT"));
        when(requete.getMethod()).thenReturn(methodeHttp);
        when(uriInfo.getPath()).thenReturn(chemin);
        when(permissionService.autorise(anyCollection(), anyString(), anyString())).thenReturn(true);

        filtre.filter(requete);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(permissionService).autorise(anyCollection(), eq("clients"), action.capture());
        return action.getValue();
    }

    @Test
    void actionDeduite_selonMethodeHttpEtChemin() throws Exception {
        assertEquals("VOIR", actionDeduitePour("GET", "clients"));
    }

    @Test
    void actionDeduite_postEstUneCreation() throws Exception {
        assertEquals("CREER", actionDeduitePour("POST", "clients"));
    }

    @Test
    void actionDeduite_putEstUneModification() throws Exception {
        assertEquals("MODIFIER", actionDeduitePour("PUT", "clients/5"));
    }

    @Test
    void actionDeduite_deleteEstUneSuppression() throws Exception {
        assertEquals("SUPPRIMER", actionDeduitePour("DELETE", "clients/5"));
    }

    @Test
    void actionDeduite_cheminDeDesactivation_prioritaireSurLaMethode() throws Exception {
        assertEquals("DESACTIVER", actionDeduitePour("POST", "clients/5/desactiver"));
    }

    @Test
    void actionDeduite_importEstUneCreation() throws Exception {
        assertEquals("CREER", actionDeduitePour("POST", "clients/import"));
    }

    @Test
    void contexteDeSecurite_exposeUtilisateurEtRoles() throws Exception {
        cibler(RessourceSessionSeule.class, "libre");
        connecter(utilisateur("AGENT"));

        filtre.filter(requete);

        ArgumentCaptor<SecurityContext> sc = ArgumentCaptor.forClass(SecurityContext.class);
        verify(requete).setSecurityContext(sc.capture());
        assertEquals("test", sc.getValue().getUserPrincipal().getName());
        assertTrue(sc.getValue().isUserInRole("AGENT"));
    }
}
