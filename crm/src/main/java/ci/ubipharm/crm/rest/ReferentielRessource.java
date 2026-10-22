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

import ci.ubipharm.crm.rest.service.ReferentielService;
import ci.ubipharm.crm.rest.service.dto.ReferentielDTO;
import ci.ubipharm.crm.rest.service.dto.ReferentielDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Regions, villes, communes et tournees : lues par les fiches clients, tenues par le titulaire du droit Referentiels. */
@RestController
@RequestMapping("/api/referentiels")
@RequiredArgsConstructor
public class ReferentielRessource {

    private static final String GESTION = "hasAuthority('DROIT_REFERENTIELS')";

    private final ReferentielService referentielService;

    @GetMapping
    public List<ReferentielDTO> lister(@RequestParam(required = false) String type, @RequestParam(defaultValue = "false") boolean archives) {
        return referentielService.lister(type, archives);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public ReferentielDTO creer(@Valid @RequestBody ReferentielDemandeDTO demande) {
        return referentielService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    public ReferentielDTO modifier(@PathVariable Long id, @Valid @RequestBody ReferentielDemandeDTO demande) {
        return referentielService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(GESTION)
    public ReferentielDTO archiver(@PathVariable Long id) {
        return referentielService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(GESTION)
    public ReferentielDTO reactiver(@PathVariable Long id) {
        return referentielService.reactiver(id);
    }
}
