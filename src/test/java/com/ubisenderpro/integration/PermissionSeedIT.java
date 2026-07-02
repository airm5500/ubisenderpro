package com.ubisenderpro.integration;

import com.ubisenderpro.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static com.ubisenderpro.integration.EnvironnementIntegration.dansTransaction;
import static com.ubisenderpro.integration.EnvironnementIntegration.injecter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration du seed RBAC (PermissionService#initPermissions) contre la
 * base migrée : le seed doit être idempotent (rejouable au démarrage sans créer
 * de doublons), donner tous les droits à ADMIN, limiter LECTURE au VOIR, et ne
 * pas écraser les réglages faits ensuite dans l'écran « Rôles & permissions ».
 */
class PermissionSeedIT {

    private EntityManager em;
    private PermissionService service;

    @BeforeEach
    void preparer() {
        em = EnvironnementIntegration.emf().createEntityManager();
        service = injecter(new PermissionService(), em);
        dansTransaction(em, service::initPermissions);
    }

    @AfterEach
    void fermer() {
        if (em.getTransaction().isActive()) { em.getTransaction().rollback(); }
        em.close();
    }

    private long compter(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    @Test
    void seed_estIdempotent() {
        long menus = compter("SELECT COUNT(m) FROM Menu m");
        long actions = compter("SELECT COUNT(a) FROM MenuAction a");
        long permissions = compter("SELECT COUNT(p) FROM RolePermission p");
        assertTrue(menus > 0 && actions > 0 && permissions > 0, "le seed doit remplir le catalogue RBAC");

        dansTransaction(em, service::initPermissions);
        em.clear();

        assertEquals(menus, compter("SELECT COUNT(m) FROM Menu m"), "menus dupliqués au rejeu du seed");
        assertEquals(actions, compter("SELECT COUNT(a) FROM MenuAction a"), "actions dupliquées au rejeu du seed");
        assertEquals(permissions, compter("SELECT COUNT(p) FROM RolePermission p"),
                "permissions dupliquées au rejeu du seed");
    }

    @Test
    void admin_recoitToutesLesPermissionsDuCatalogue() {
        long actions = compter("SELECT COUNT(a) FROM MenuAction a");
        long admin = compter("SELECT COUNT(p) FROM RolePermission p WHERE p.roleCode = 'ADMIN'");
        assertEquals(actions, admin, "ADMIN doit posséder chaque action du catalogue");
    }

    @Test
    void lecture_estLimiteAuVoir() {
        List<String> actions = em.createQuery(
                "SELECT DISTINCT p.actionCode FROM RolePermission p WHERE p.roleCode = 'LECTURE'",
                String.class).getResultList();
        assertFalse(actions.isEmpty(), "le rôle LECTURE doit être semé");
        assertEquals(Arrays.asList("VOIR"), actions, "LECTURE ne doit avoir que le droit VOIR");
    }

    @Test
    void autorisationsParDefaut_refleteLaGrilleDesMenus() {
        LinkedHashSet<String> marketing = new LinkedHashSet<>(Arrays.asList("MARKETING"));
        assertTrue(service.autorise(marketing, "campaigns", "ENVOYER"),
                "MARKETING doit pouvoir envoyer des campagnes par défaut");
        assertFalse(service.autorise(marketing, "users", "CREER"),
                "MARKETING ne doit pas gérer les utilisateurs");
        assertFalse(service.autorise(new LinkedHashSet<>(Arrays.asList("LECTURE")), "clients", "MODIFIER"),
                "LECTURE ne doit pas modifier les clients");
    }

    @Test
    void reglagesPersonnalises_nonEcrasesParLeSeed() {
        // Un admin retire un droit dans l'écran « Rôles & permissions »…
        dansTransaction(em, () -> service.definirPermissionsRole("AGENT",
                Arrays.asList("dashboard:VOIR", "clients:VOIR")));
        // …puis l'application redémarre (le seed est rejoué au bootstrap).
        dansTransaction(em, service::initPermissions);
        em.clear();

        long agent = compter("SELECT COUNT(p) FROM RolePermission p WHERE p.roleCode = 'AGENT'");
        assertEquals(2, agent, "le seed ne doit pas ré-initialiser un rôle déjà configuré");
    }
}
