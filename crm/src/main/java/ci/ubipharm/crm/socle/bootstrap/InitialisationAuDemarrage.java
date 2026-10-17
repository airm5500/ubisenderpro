package ci.ubipharm.crm.socle.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.securite.rest.service.InitialisationAdminService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InitialisationAuDemarrage implements ApplicationRunner {

    private final InitialisationAdminService initialisationAdminService;

    @Override
    public void run(ApplicationArguments arguments) {
        initialisationAdminService.initialiserMotDePasseAdmin();
    }
}
