package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.AgenceService;
import ci.ubipharm.crm.rest.service.dto.AgenceDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceGestionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/agences")
@RequiredArgsConstructor
public class AgenceRessource {

    private static final String GESTION = "hasAuthority('DROIT_AGENCES')";

    private final AgenceService agenceService;

    @GetMapping
    public List<AgenceDTO> lister() {
        return agenceService.listerVisibles();
    }

    @GetMapping("/gestion")
    @PreAuthorize(GESTION)
    public List<AgenceGestionDTO> listerPourGestion(@RequestParam(defaultValue = "false") boolean archives) {
        return agenceService.lister(archives);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public AgenceGestionDTO creer(@Valid @RequestBody AgenceDemandeDTO demande) {
        return agenceService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    public AgenceGestionDTO modifier(@PathVariable Long id, @Valid @RequestBody AgenceDemandeDTO demande) {
        return agenceService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(GESTION)
    public AgenceGestionDTO archiver(@PathVariable Long id) {
        return agenceService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(GESTION)
    public AgenceGestionDTO reactiver(@PathVariable Long id) {
        return agenceService.reactiver(id);
    }
}
