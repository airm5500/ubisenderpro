package ci.ubipharm.crm.planification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.CampagneMarketingService;
import ci.ubipharm.crm.rest.service.CampagneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Demarre chaque minute les campagnes validees (relances et promotions) dont la date d'execution est atteinte. */
@Slf4j
@Component
@ConditionalOnProperty(name = "crm.campagnes.planificateur", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PlanificateurCampagnes {

    private final CampagneService campagneService;
    private final CampagneMarketingService campagneMarketingService;

    @Scheduled(fixedDelayString = "${crm.campagnes.intervalle:60000}", initialDelay = 30000)
    public void demarrerCampagnesEchues() {
        int demarrees = campagneService.demarrerCampagnesEchues() + campagneMarketingService.demarrerCampagnesEchues();
        if (demarrees > 0) {
            log.info("{} campagne(s) demarree(s)", demarrees);
        }
    }
}
