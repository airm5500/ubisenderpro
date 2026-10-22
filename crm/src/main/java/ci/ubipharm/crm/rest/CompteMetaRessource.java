package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.CompteMetaService;
import ci.ubipharm.crm.rest.service.dto.CompteMetaDTO;
import ci.ubipharm.crm.rest.service.dto.CompteMetaDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Compte de l'API WhatsApp Cloud de Meta, utilise par les numeros passes a l'API officielle. */
@RestController
@RequestMapping("/api/whatsapp/meta")
@PreAuthorize("hasAuthority('DROIT_WHATSAPP_META')")
@RequiredArgsConstructor
public class CompteMetaRessource {

    private final CompteMetaService compteMetaService;

    @GetMapping
    public CompteMetaDTO consulter() {
        return compteMetaService.consulter();
    }

    @PutMapping
    public CompteMetaDTO modifier(@Valid @RequestBody CompteMetaDemandeDTO demande) {
        return compteMetaService.modifier(demande);
    }

    @PostMapping("/jeton-verification")
    public CompteMetaDTO renouvelerJetonVerification() {
        return compteMetaService.renouvelerJetonVerification();
    }
}
