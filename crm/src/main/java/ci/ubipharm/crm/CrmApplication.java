package ci.ubipharm.crm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrmApplication {

    // Toutes les dates sont en UTC (utc_timestamp() en base, LocalDateTime.now(UTC) en Java) : sur un poste
    // regle sur un autre fuseau, la lecture des DATETIME serait decalee d'autant (sessions fermees a tort).
    // Bloc statique : vaut aussi pour les tests, qui chargent la classe sans passer par main.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
