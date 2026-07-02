package com.ubisenderpro.integration;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.MariaDBContainer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure partagée des tests d'intégration : une MariaDB éphémère
 * (Testcontainers) démarrée une seule fois pour toute la JVM, migrée par
 * Flyway exactement comme au démarrage de l'application (Bootstrap), puis
 * exposée via une EntityManagerFactory EclipseLink.
 *
 * <p>Prérequis : Docker. Si le démon est très récent (Docker 29+) et refuse la
 * connexion avec « client version 1.32 is too old », lancer :
 * {@code mvn verify -Dapi.version=1.44}.</p>
 */
public final class EnvironnementIntegration {

    /** Même image majeure que la production (MariaDB). */
    private static final String IMAGE = "mariadb:10.6";

    private static MariaDBContainer<?> conteneur;
    private static EntityManagerFactory emf;
    private static int migrationsAppliqueesAuPremierPassage = -1;

    private EnvironnementIntegration() { }

    public static synchronized MariaDBContainer<?> conteneur() {
        if (conteneur == null) {
            conteneur = new MariaDBContainer<>(IMAGE)
                    .withDatabaseName("ubisenderpro_db")
                    .withUsername("test")
                    .withPassword("test");
            conteneur.start();
            migrationsAppliqueesAuPremierPassage = flyway().migrate().migrationsExecuted;
        }
        return conteneur;
    }

    /** Flyway configuré comme dans Bootstrap#runMigrations (mêmes emplacements). */
    public static Flyway flyway() {
        MariaDBContainer<?> c = conteneur == null ? conteneur() : conteneur;
        return Flyway.configure(Thread.currentThread().getContextClassLoader())
                .dataSource(c.getJdbcUrl(), c.getUsername(), c.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    /** Nombre de migrations appliquées lors du tout premier migrate (base vierge). */
    public static int migrationsInitiales() {
        conteneur();
        return migrationsAppliqueesAuPremierPassage;
    }

    public static synchronized EntityManagerFactory emf() {
        if (emf == null) {
            MariaDBContainer<?> c = conteneur();
            Map<String, String> props = new HashMap<>();
            props.put("javax.persistence.jdbc.url", c.getJdbcUrl());
            props.put("javax.persistence.jdbc.user", c.getUsername());
            props.put("javax.persistence.jdbc.password", c.getPassword());
            emf = Persistence.createEntityManagerFactory("ubisenderproTestPU", props);
        }
        return emf;
    }

    /**
     * Injecte l'EntityManager dans le champ {@code em} d'un service @Stateless,
     * comme le ferait le conteneur EJB via @PersistenceContext.
     */
    public static <T> T injecter(T service, EntityManager em) {
        try {
            Field champ = service.getClass().getDeclaredField("em");
            champ.setAccessible(true);
            champ.set(service, em);
            return service;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Impossible d'injecter l'EntityManager dans " + service.getClass().getSimpleName(), e);
        }
    }

    /** Exécute une action dans une transaction (commit, ou rollback si elle lève). */
    public static void dansTransaction(EntityManager em, Runnable action) {
        em.getTransaction().begin();
        try {
            action.run();
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) { em.getTransaction().rollback(); }
            throw e;
        }
    }
}
