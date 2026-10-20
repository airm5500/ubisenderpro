package ci.ubipharm.crm.planification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import ci.ubipharm.crm.rest.service.SupportExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaque nuit, retire du diagnostic les erreurs sans survenue depuis support.retention_jours
 * et de l'experience utilisateur les mesures plus anciennes que support.rum_retention_jours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanificateurSupport {

    private final SupportDiagnosticService supportDiagnosticService;
    private final SupportExperienceService supportExperienceService;

    @Scheduled(cron = "${crm.support.purge-cron:0 30 3 * * *}")
    public void purgerDiagnostic() {
        try {
            int n = supportDiagnosticService.purger();
            if (n > 0) {
                log.info("{} erreur(s) du diagnostic purgee(s)", n);
            }
        } catch (RuntimeException e) {
            log.warn("Purge du diagnostic impossible : {}", e.getMessage());
        }
        try {
            int n = supportExperienceService.purger();
            if (n > 0) {
                log.info("{} mesure(s) de l'experience utilisateur purgee(s)", n);
            }
        } catch (RuntimeException e) {
            log.warn("Purge de l'experience utilisateur impossible : {}", e.getMessage());
        }
    }
}
