package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.EnvoiEmailService;
import ci.ubipharm.crm.rest.service.dto.EtatEmailDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/email")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class EmailRessource {

    private final EnvoiEmailService envoiEmailService;

    @GetMapping("/etat")
    public EtatEmailDTO etat() {
        return envoiEmailService.etat();
    }
}
