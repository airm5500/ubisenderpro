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

import ci.ubipharm.crm.rest.service.DelaiRelanceService;
import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDTO;
import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recouvrement/delais-relance")
@PreAuthorize("hasAuthority('DROIT_DELAIS_RELANCE')")
@RequiredArgsConstructor
public class DelaiRelanceRessource {

    private final DelaiRelanceService delaiRelanceService;

    @GetMapping("/defauts")
    public DelaiRelanceDTO defauts() {
        return delaiRelanceService.defauts();
    }

    @GetMapping
    public List<DelaiRelanceDTO> lister(@RequestParam(defaultValue = "false") boolean archives) {
        return delaiRelanceService.lister(archives);
    }

    @PostMapping
    public DelaiRelanceDTO creer(@Valid @RequestBody DelaiRelanceDemandeDTO demande) {
        return delaiRelanceService.creer(demande);
    }

    @PutMapping("/{id}")
    public DelaiRelanceDTO modifier(@PathVariable Long id, @Valid @RequestBody DelaiRelanceDemandeDTO demande) {
        return delaiRelanceService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    public DelaiRelanceDTO archiver(@PathVariable Long id) {
        return delaiRelanceService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    public DelaiRelanceDTO reactiver(@PathVariable Long id) {
        return delaiRelanceService.reactiver(id);
    }
}
