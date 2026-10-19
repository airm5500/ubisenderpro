package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiSmsService;
import ci.ubipharm.crm.rest.service.dto.EtatSmsDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sms")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class SmsRessource {

    private final EnvoiSmsService envoiSmsService;

    @GetMapping("/etat")
    public EtatSmsDTO etat() {
        return envoiSmsService.etat();
    }
}
