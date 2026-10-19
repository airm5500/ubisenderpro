package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.DiscussionService;
import ci.ubipharm.crm.rest.service.dto.DiscussionCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.DiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.MessageDiscussionDTO;
import ci.ubipharm.crm.rest.service.dto.ReponseDiscussionDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Discussions WhatsApp avec les clients. */
@RestController
@RequestMapping("/api/discussions")
@PreAuthorize("hasAuthority('DROIT_DISCUSSIONS')")
@RequiredArgsConstructor
public class DiscussionRessource {

    private final DiscussionService discussionService;

    @GetMapping
    public PageDTO<DiscussionDTO> rechercher(@Valid DiscussionCriteresDTO criteres) {
        return discussionService.rechercher(criteres);
    }

    @GetMapping("/messages")
    public List<MessageDiscussionDTO> fil(@RequestParam String numeroAgent, @RequestParam String numeroClient) {
        return discussionService.fil(numeroAgent, numeroClient);
    }

    @PostMapping("/messages")
    public MessageDiscussionDTO repondre(@Valid @RequestBody ReponseDiscussionDTO reponse) {
        return discussionService.repondre(reponse);
    }
}
