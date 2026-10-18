package ci.ubipharm.crm.email.planification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.email.rest.service.EnvoiEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envoie les relances par e-mail en attente ; le debit reel est regle par les parametres email.envoi.*. */
@Slf4j
@Component
@ConditionalOnProperty(name = "crm.email.envoi", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PlanificateurEnvoiEmail {

    private final EnvoiEmailService envoiEmailService;

    @EventListener(ApplicationReadyEvent.class)
    public void reprendre() {
        int interrompus = envoiEmailService.reprendreEnvoisInterrompus();
        if (interrompus > 0) {
            log.warn("{} e-mail(s) interrompu(s) par l'arret precedent passes en echec, a verifier", interrompus);
        }
    }

    @Scheduled(fixedDelayString = "${crm.email.intervalle:1000}", initialDelay = 15000)
    public void envoyer() {
        envoiEmailService.envoyerSuivant();
    }
}
