package com.ubisenderpro.security;

import com.ubisenderpro.rest.PermissionResource;
import com.ubisenderpro.rest.UserResource;
import org.junit.jupiter.api.Test;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-régression sur la politique de sécurité des endpoints REST.
 *
 * <p>Vérifie par réflexion que :</p>
 * <ul>
 *   <li>toute ressource JAX-RS (@Path) est protégée par @Secured — au niveau de la
 *       classe ou de chacune de ses méthodes HTTP — sauf les endpoints publics
 *       assumés (login, webhooks Meta, « À propos ») ;</li>
 *   <li>la gestion des rôles et des permissions reste réservée au rôle ADMIN.</li>
 * </ul>
 *
 * <p>Objectif : qu'un nouvel endpoint ajouté sans protection, ou une restriction
 * ADMIN retirée par mégarde, fasse échouer la CI.</p>
 */
class PolitiqueSecuriteTest {

    /** Ressources publiques assumées (pas de session requise). */
    private static final Set<String> CLASSES_PUBLIQUES = new HashSet<>(Arrays.asList(
            "AboutResource",      // version de l'application
            "WebhookResource",    // webhook entrant Meta/WhatsApp (vérifié par token dédié)
            "WaWebEventResource"  // webhook entrant WhatsApp Web
    ));

    /** Méthodes publiques assumées dans des ressources par ailleurs protégées. */
    private static final Set<String> METHODES_PUBLIQUES = new HashSet<>(Arrays.asList(
            "AuthResource#login",
            "MediaResource#telecharger" // média servi par lien aux serveurs WhatsApp (assumé public)
    ));

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> VERBES_HTTP = Arrays.asList(
            GET.class, POST.class, PUT.class, DELETE.class,
            PATCH.class, HEAD.class, OPTIONS.class);

    /* ------------------- Tous les endpoints sont protégés ------------------- */

    @Test
    void toutEndpointRest_estProtegeParSecured_ouExplicitementPublic() throws Exception {
        List<Class<?>> ressources = classesRest();
        assertTrue(ressources.size() >= 40,
                "le scan doit trouver les ressources REST (trouvées : " + ressources.size() + ")");

        List<String> violations = new ArrayList<>();
        for (Class<?> c : ressources) {
            if (!c.isAnnotationPresent(Path.class)) { continue; }
            if (CLASSES_PUBLIQUES.contains(c.getSimpleName())) { continue; }
            if (c.isAnnotationPresent(Secured.class)) { continue; }
            // Pas de @Secured sur la classe : chaque méthode HTTP doit l'avoir.
            for (Method m : c.getDeclaredMethods()) {
                if (!estMethodeHttp(m)) { continue; }
                if (m.isAnnotationPresent(Secured.class)) { continue; }
                if (METHODES_PUBLIQUES.contains(c.getSimpleName() + "#" + m.getName())) { continue; }
                violations.add(c.getSimpleName() + "#" + m.getName());
            }
        }
        assertTrue(violations.isEmpty(),
                "Endpoints REST accessibles sans @Secured (à protéger ou à déclarer publics) : " + violations);
    }

    /* -------------- La gestion RBAC reste réservée au rôle ADMIN -------------- */

    @Test
    void gestionDesPermissions_reserveeALAdmin() throws Exception {
        exigeRoleAdmin(PermissionResource.class, "menus");
        exigeRoleAdmin(PermissionResource.class, "permissionsRole");
        exigeRoleAdmin(PermissionResource.class, "definir");
    }

    @Test
    void gestionDesRoles_reserveeALAdmin() throws Exception {
        exigeRoleAdmin(UserResource.class, "creerRole");
        exigeRoleAdmin(UserResource.class, "tousRoles");
        exigeRoleAdmin(UserResource.class, "modifierRole");
        exigeRoleAdmin(UserResource.class, "actifRole");
    }

    @Test
    void lectureDesDroitsCourants_nExigePasAdmin() throws Exception {
        // /permissions/me sert à l'affichage des menus de tout utilisateur connecté :
        // il doit rester accessible sans le rôle ADMIN (mais avec une session).
        Method me = methode(PermissionResource.class, "mesPermissions");
        Secured s = me.getAnnotation(Secured.class);
        boolean exigeAdmin = s != null && Arrays.asList(s.roles()).contains("ADMIN");
        assertFalse(exigeAdmin, "/permissions/me ne doit pas être réservé à l'ADMIN");
    }

    /* --------------------------------- Aides --------------------------------- */

    private static void exigeRoleAdmin(Class<?> classe, String nomMethode) throws Exception {
        Method m = methode(classe, nomMethode);
        Secured s = m.getAnnotation(Secured.class);
        assertNotNull(s, classe.getSimpleName() + "#" + nomMethode + " doit porter @Secured");
        assertTrue(Arrays.asList(s.roles()).contains("ADMIN"),
                classe.getSimpleName() + "#" + nomMethode + " doit être réservé au rôle ADMIN");
    }

    private static Method methode(Class<?> classe, String nom) {
        for (Method m : classe.getDeclaredMethods()) {
            if (m.getName().equals(nom)) { return m; }
        }
        throw new AssertionError("Méthode introuvable : " + classe.getSimpleName() + "#" + nom
                + " (renommée ? mettre à jour ce test)");
    }

    private static boolean estMethodeHttp(Method m) {
        if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) { return false; }
        for (Class<? extends Annotation> verbe : VERBES_HTTP) {
            if (m.isAnnotationPresent(verbe)) { return true; }
        }
        return false;
    }

    /** Charge toutes les classes compilées du package com.ubisenderpro.rest. */
    private static List<Class<?>> classesRest() throws Exception {
        File racine = new File(PermissionResource.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File dossier = new File(racine, "com/ubisenderpro/rest");
        assertTrue(dossier.isDirectory(), "répertoire des classes REST introuvable : " + dossier);
        List<Class<?>> classes = new ArrayList<>();
        File[] fichiers = dossier.listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"));
        if (fichiers != null) {
            for (File f : fichiers) {
                String nom = f.getName().substring(0, f.getName().length() - ".class".length());
                classes.add(Class.forName("com.ubisenderpro.rest." + nom));
            }
        }
        return classes;
    }
}
