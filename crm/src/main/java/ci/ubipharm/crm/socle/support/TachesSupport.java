package ci.ubipharm.crm.socle.support;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Un seul fil de fond pour les ecritures du centre de support qui ne doivent pas allonger une reponse
 * (requete lente, e-mail d'un ticket automatique). File bornee : au-dela de 200 taches en attente, les nouvelles sont ignorees.
 */
@Slf4j
@Component
public class TachesSupport {

    private final ThreadPoolExecutor executeur = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(200), r -> {
        Thread fil = new Thread(r, "crm-support");
        fil.setDaemon(true);
        return fil;
    }, new ThreadPoolExecutor.DiscardPolicy());

    /** Execute la tache en arriere-plan ; une tache en echec est journalisee sans rien interrompre. */
    public void lancer(Runnable tache) {
        executeur.execute(() -> {
            try {
                tache.run();
            } catch (Throwable t) {
                log.warn("Tache du centre de support en echec : {}", t.toString());
            }
        });
    }

    @PreDestroy
    void arreter() {
        executeur.shutdown();
    }
}
