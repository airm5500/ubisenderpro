package ci.ubipharm.crm.socle.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ci.ubipharm.crm.rest.service.ChiffrementService;
import ci.ubipharm.crm.rest.service.InitialisationAdminService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InitialisationAuDemarrage implements ApplicationRunner {

    private final InitialisationAdminService initialisationAdminService;
    private final ChiffrementService chiffrementService;

    @Override
    public void run(ApplicationArguments arguments) {
        initialisationAdminService.initialiserMotDePasseAdmin();
        chiffrementService.chiffrerSecretsEnClair();
    }
}
