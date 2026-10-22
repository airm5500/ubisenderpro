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

import ci.ubipharm.crm.rest.service.EvenementService;
import ci.ubipharm.crm.rest.service.dto.EvenementDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing/evenements")
@PreAuthorize("hasAnyAuthority('DROIT_EVENEMENTS', 'DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class EvenementRessource {

    private static final String GESTION = "hasAuthority('DROIT_EVENEMENTS')";

    private final EvenementService evenementService;

    @GetMapping
    public List<EvenementDTO> lister(@RequestParam(defaultValue = "false") boolean archives) {
        return evenementService.lister(archives);
    }

    @GetMapping("/{id}")
    public EvenementDTO consulter(@PathVariable Long id) {
        return evenementService.consulter(id);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public EvenementDTO creer(@Valid @RequestBody EvenementDemandeDTO demande) {
        return evenementService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    public EvenementDTO modifier(@PathVariable Long id, @Valid @RequestBody EvenementDemandeDTO demande) {
        return evenementService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(GESTION)
    public EvenementDTO archiver(@PathVariable Long id) {
        return evenementService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(GESTION)
    public EvenementDTO reactiver(@PathVariable Long id) {
        return evenementService.reactiver(id);
    }
}
