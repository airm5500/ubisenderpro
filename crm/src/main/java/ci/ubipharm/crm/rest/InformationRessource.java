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

import ci.ubipharm.crm.rest.service.InformationService;
import ci.ubipharm.crm.rest.service.dto.InformationDTO;
import ci.ubipharm.crm.rest.service.dto.InformationDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing/informations")
@PreAuthorize("hasAnyAuthority('DROIT_INFORMATIONS', 'DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class InformationRessource {

    private static final String GESTION = "hasAuthority('DROIT_INFORMATIONS')";

    private final InformationService informationService;

    @GetMapping
    public List<InformationDTO> lister(@RequestParam(defaultValue = "false") boolean archives) {
        return informationService.lister(archives);
    }

    @GetMapping("/{id}")
    public InformationDTO consulter(@PathVariable Long id) {
        return informationService.consulter(id);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public InformationDTO creer(@Valid @RequestBody InformationDemandeDTO demande) {
        return informationService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    public InformationDTO modifier(@PathVariable Long id, @Valid @RequestBody InformationDemandeDTO demande) {
        return informationService.modifier(id, demande);
    }

    @PostMapping("/{id}/duplication")
    @PreAuthorize(GESTION)
    public InformationDTO dupliquer(@PathVariable Long id) {
        return informationService.dupliquer(id);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(GESTION)
    public InformationDTO archiver(@PathVariable Long id) {
        return informationService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(GESTION)
    public InformationDTO reactiver(@PathVariable Long id) {
        return informationService.reactiver(id);
    }
}
