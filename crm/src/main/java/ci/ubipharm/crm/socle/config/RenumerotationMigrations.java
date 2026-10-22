package ci.ubipharm.crm.socle.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/**
 * Numerotation des migrations : V1.0.1 a V1.0.9, puis V1.1.0 a V1.1.9, puis V1.2.0... (decision d'Airman du 19/10/2026).
 * Une base migree avant ce changement porte encore les versions 1.0.10 a 1.0.33 : son historique Flyway est renomme une fois,
 * avant la validation, pour que les scripts deja passes ne soient ni rejoues ni signales comme inconnus.
 */
@Component
public class RenumerotationMigrations implements Callback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_VALIDATE || event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connexion = context.getConnection();
        try (Statement s = connexion.createStatement()) {
            try (ResultSet table = connexion.getMetaData().getTables(connexion.getCatalog(), null, "flyway_schema_history", null)) {
                if (!table.next()) {
                    return;
                }
            }
            // 1.0.NN (deux chiffres) devient 1.N.N ; le nom du script suit
            s.executeUpdate("""
                    update flyway_schema_history
                    set script = concat('V1.', substr(version, 5, 1), '.', substr(version, 6, 1), substr(script, length(version) + 2)),
                        version = concat('1.', substr(version, 5, 1), '.', substr(version, 6, 1))
                    where version regexp '^1\\\\.0\\\\.[1-9][0-9]$'
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Renumérotation de l'historique des migrations impossible", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "RenumerotationMigrations";
    }
}
