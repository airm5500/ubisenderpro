package com.ubisenderpro.integration;

import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration des migrations Flyway contre une vraie MariaDB
 * (Testcontainers) : le schéma complet doit s'appliquer sans erreur sur une
 * base vierge, être idempotent au rejeu, et produire les tables essentielles.
 */
class FlywayMigrationIT {

    @Test
    void baseVierge_touteLesMigrationsSAppliquent() {
        int appliquees = EnvironnementIntegration.migrationsInitiales();
        int scripts = scriptsDeMigration().size();
        assertTrue(scripts > 0, "aucun script de migration trouvé sur le classpath");
        assertEquals(scripts, appliquees,
                "chaque script db/migration doit avoir été appliqué sur la base vierge");
    }

    @Test
    void rejeu_estIdempotent() {
        // Deuxième migrate sur la même base : rien à faire, aucune erreur.
        int rejouees = EnvironnementIntegration.flyway().migrate().migrationsExecuted;
        assertEquals(0, rejouees, "rejouer les migrations ne doit rien appliquer de plus");
    }

    @Test
    void historiqueFlyway_sansEchec() {
        for (MigrationInfo info : EnvironnementIntegration.flyway().info().applied()) {
            assertTrue(info.getState().isApplied(),
                    "migration en état anormal : " + info.getVersion() + " -> " + info.getState());
        }
        assertNotNull(EnvironnementIntegration.flyway().info().current(), "aucune migration courante");
    }

    @Test
    void tablesEssentielles_presentes() throws Exception {
        List<String> attendues = Arrays.asList(
                "usp_utilisateur", "usp_client", "usp_campagne",
                "usp_menu", "usp_menu_action", "usp_role_menu_action",
                "usp_rec_fiche", "usp_rec_creance", "usp_rec_paiement",
                "usp_rec_promesse", "usp_rec_referentiel");
        try (Connection cx = connexion()) {
            for (String table : attendues) {
                try (PreparedStatement ps = cx.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = ?")) {
                    ps.setString(1, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), "table manquante après migration : " + table);
                    }
                }
            }
        }
    }

    @Test
    void uniciteFicheRecouvrement_garantieParLeSchema() throws Exception {
        // La règle « une fiche par client » est aussi portée par le schéma
        // (clé unique sur client_id), pas seulement par le service.
        try (Connection cx = connexion();
             PreparedStatement ps = cx.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.statistics "
                             + "WHERE table_schema = DATABASE() AND table_name = 'usp_rec_fiche' "
                             + "AND column_name = 'client_id' AND non_unique = 0");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertTrue(rs.getInt(1) >= 1, "index unique manquant sur usp_rec_fiche.client_id");
        }
    }

    /* --------------------------------- Aides --------------------------------- */

    private static Connection connexion() throws Exception {
        return DriverManager.getConnection(
                EnvironnementIntegration.conteneur().getJdbcUrl(),
                EnvironnementIntegration.conteneur().getUsername(),
                EnvironnementIntegration.conteneur().getPassword());
    }

    /** Fichiers V*.sql présents dans db/migration sur le classpath de test. */
    private static List<String> scriptsDeMigration() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("db/migration");
        assertNotNull(url, "répertoire db/migration introuvable sur le classpath");
        File dossier = new File(url.getPath());
        List<String> scripts = new ArrayList<>();
        String[] fichiers = dossier.list((d, n) -> n.matches("V.+__.+\\.sql"));
        if (fichiers != null) { scripts.addAll(Arrays.asList(fichiers)); }
        return scripts;
    }
}
