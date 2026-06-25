package com.ubisenderpro.config;

import com.ubisenderpro.entity.ModeleMessage;
import com.ubisenderpro.security.PasswordHasher;
import com.ubisenderpro.service.PromoTemplates;
import org.flywaydb.core.Flyway;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Démarrage de l'application :
 *  1. exécute les migrations Flyway sur la base ubisenderpro_db ;
 *  2. initialise le mot de passe du compte admin (haché en BCrypt) au premier lancement.
 */
@Singleton
@Startup
public class Bootstrap {

    private static final Logger LOG = Logger.getLogger(Bootstrap.class.getName());

    @Resource(lookup = "UbiSenderProDS")
    private DataSource dataSource;

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @PostConstruct
    public void init() {
        runMigrations();
        initAdminPassword();
        initModelesPromo();
    }

    private void runMigrations() {
        // Permet de provisionner la base manuellement (script db/install) sans Flyway :
        // exporter UBISENDERPRO_SKIP_FLYWAY=true ou -Dubisenderpro.skipFlyway=true.
        if ("true".equalsIgnoreCase(System.getenv("UBISENDERPRO_SKIP_FLYWAY"))
                || "true".equalsIgnoreCase(System.getProperty("ubisenderpro.skipFlyway"))) {
            LOG.info("UbiSenderPro : migrations Flyway désactivées (provisionnement manuel).");
            return;
        }
        try {
            Flyway flyway = Flyway.configure(Thread.currentThread().getContextClassLoader())
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
            LOG.info("UbiSenderPro : migrations Flyway appliquées.");
        } catch (Exception e) {
            LOG.severe("Echec des migrations Flyway : " + e.getMessage());
            throw new IllegalStateException("Migrations Flyway en échec", e);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void initAdminPassword() {
        try {
            Object hash = em.createNativeQuery(
                    "SELECT mot_de_passe_hash FROM usp_utilisateur WHERE login = 'admin'")
                    .getSingleResult();
            if (hash != null && !String.valueOf(hash).startsWith("$2")) {
                String motDePasse = (String) em.createNativeQuery(
                        "SELECT valeur FROM usp_parametre WHERE cle = 'admin.mot_de_passe_initial'")
                        .getSingleResult();
                String nouveauHash = PasswordHasher.hash(motDePasse);
                em.createNativeQuery("UPDATE usp_utilisateur SET mot_de_passe_hash = ?1 WHERE login = 'admin'")
                        .setParameter(1, nouveauHash)
                        .executeUpdate();
                LOG.info("UbiSenderPro : mot de passe admin initialisé.");
            }
        } catch (Exception e) {
            LOG.warning("Initialisation du mot de passe admin ignorée : " + e.getMessage());
        }
    }

    /** Crée les 4 modèles de message « promotion » prédéfinis s'ils sont absents. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void initModelesPromo() {
        for (Map.Entry<String, String> entry : PromoTemplates.CORPS.entrySet()) {
            String cle = entry.getKey();
            try {
                Long n = em.createQuery(
                        "SELECT COUNT(m) FROM ModeleMessage m WHERE m.cleSysteme = :c", Long.class)
                        .setParameter("c", cle).getSingleResult();
                if (n != null && n > 0) { continue; }
                ModeleMessage m = new ModeleMessage();
                m.setNom(PromoTemplates.NOMS.get(cle));
                m.setTypeModele("PROMOTION");
                m.setCategorie("MARKETING");
                m.setLangue("fr");
                m.setCorps(entry.getValue());
                m.setCleSysteme(cle);
                m.setStatutApprobation("BROUILLON");
                m.setActif(true);
                em.persist(m);
                LOG.info("UbiSenderPro : modèle promo créé (" + cle + ").");
            } catch (Exception e) {
                LOG.warning("Création du modèle promo " + cle + " ignorée : " + e.getMessage());
            }
        }
    }
}
