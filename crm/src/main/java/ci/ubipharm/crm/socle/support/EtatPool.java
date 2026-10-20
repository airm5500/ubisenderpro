package ci.ubipharm.crm.socle.support;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/** Etat du pool de connexions a la base (Hikari) : connexions occupees, ouvertes, maximum et fils en attente d'une connexion. */
public record EtatPool(int actives, int ouvertes, int maximum, int enAttente) {

    /** Etat courant, ou null si le pool n'est pas un pool Hikari demarre. */
    public static EtatPool lire(DataSource source) {
        try {
            if (!source.isWrapperFor(HikariDataSource.class)) {
                return null;
            }
            HikariDataSource hikari = source.unwrap(HikariDataSource.class);
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) {
                return null;
            }
            return new EtatPool(pool.getActiveConnections(), pool.getTotalConnections(), hikari.getMaximumPoolSize(),
                    pool.getThreadsAwaitingConnection());
        } catch (Exception e) {
            return null;
        }
    }
}
