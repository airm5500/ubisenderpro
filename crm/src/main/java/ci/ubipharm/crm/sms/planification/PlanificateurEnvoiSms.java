package ci.ubipharm.crm.sms.planification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.sms.rest.service.EnvoiSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envoie les relances SMS en attente (debit regle par sms.envoi.*) et interroge le fournisseur sur les accuses manquants. */
@Slf4j
@Component
@ConditionalOnProperty(name = "crm.sms.envoi", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PlanificateurEnvoiSms {

    private final EnvoiSmsService envoiSmsService;

    @EventListener(ApplicationReadyEvent.class)
    public void reprendre() {
        int interrompus = envoiSmsService.reprendreEnvoisInterrompus();
        if (interrompus > 0) {
            log.warn("{} SMS interrompu(s) par l'arret precedent passes en echec, a verifier", interrompus);
        }
    }

    @Scheduled(fixedDelayString = "${crm.sms.intervalle:1000}", initialDelay = 15000)
    public void envoyer() {
        envoiSmsService.envoyerSuivant();
    }

    @Scheduled(fixedDelayString = "${crm.sms.verification:120000}", initialDelay = 60000)
    public void verifier() {
        envoiSmsService.verifierStatuts();
    }
}
