package ci.ubipharm.crm.commun.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.commun.rest.service.AgenceService;
import ci.ubipharm.crm.commun.rest.service.dto.AgenceDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/agences")
@RequiredArgsConstructor
public class AgenceRessource {

    private final AgenceService agenceService;

    @GetMapping
    public List<AgenceDTO> lister() {
        return agenceService.listerVisibles();
    }
}
