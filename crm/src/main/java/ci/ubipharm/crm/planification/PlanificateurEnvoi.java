package ci.ubipharm.crm.planification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.EnvoiWhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envoie les relances en attente ; le debit reel est regle par les parametres whatsapp.envoi.*. */
@Slf4j
@Component
@ConditionalOnProperty(name = "crm.whatsapp.envoi", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PlanificateurEnvoi {

    private final EnvoiWhatsappService envoiWhatsappService;

    @EventListener(ApplicationReadyEvent.class)
    public void reprendre() {
        int interrompus = envoiWhatsappService.reprendreEnvoisInterrompus();
        if (interrompus > 0) {
            log.warn("{} envoi(s) interrompu(s) par l'arret precedent passes en echec, a verifier", interrompus);
        }
    }

    @Scheduled(fixedDelayString = "${crm.whatsapp.intervalle:5000}", initialDelay = 15000)
    public void envoyer() {
        envoiWhatsappService.envoyerLot();
    }
}
