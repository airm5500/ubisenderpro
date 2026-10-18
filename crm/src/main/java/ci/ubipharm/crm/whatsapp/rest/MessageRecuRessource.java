package ci.ubipharm.crm.whatsapp.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.socle.dto.PageDTO;
import ci.ubipharm.crm.whatsapp.rest.service.MessageRecuService;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuCriteresDTO;
import ci.ubipharm.crm.whatsapp.rest.service.dto.MessageRecuDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Copie des reponses des clients aux relances (l'agent leur repond depuis son WhatsApp). */
@RestController
@RequestMapping("/api/recouvrement/reponses")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class MessageRecuRessource {

    private final MessageRecuService messageRecuService;

    @GetMapping
    public PageDTO<MessageRecuDTO> rechercher(@Valid MessageRecuCriteresDTO criteres) {
        return messageRecuService.rechercher(criteres);
    }
}
