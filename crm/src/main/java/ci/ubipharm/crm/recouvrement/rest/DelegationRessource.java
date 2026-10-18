package ci.ubipharm.crm.recouvrement.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.recouvrement.rest.service.DelegationService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.DelegationDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Interims : autorises par la Direction du Recouvrement, consultables par les agents concernes. */
@RestController
@RequestMapping("/api/recouvrement/delegations")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class DelegationRessource {

    private final DelegationService delegationService;

    @GetMapping
    public List<DelegationDTO> rechercher(DelegationCriteresDTO criteres) {
        return delegationService.rechercher(criteres);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DROIT_INTERIMS')")
    public DelegationDTO creer(@Valid @RequestBody DelegationDemandeDTO demande) {
        return delegationService.creer(demande);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize("hasAuthority('DROIT_INTERIMS')")
    public DelegationDTO annuler(@PathVariable Long id) {
        return delegationService.annuler(id);
    }
}
