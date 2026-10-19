package ci.ubipharm.crm.planification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.SupportDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Chaque nuit, retire du diagnostic les erreurs sans survenue depuis support.retention_jours. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanificateurSupport {

    private final SupportDiagnosticService supportDiagnosticService;

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
    }
}
