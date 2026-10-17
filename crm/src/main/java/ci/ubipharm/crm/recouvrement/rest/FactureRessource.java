package ci.ubipharm.crm.recouvrement.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.recouvrement.rest.service.FactureService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recouvrement")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class FactureRessource {

    private final FactureService factureService;

    @GetMapping("/factures")
    public PageDTO<FactureDTO> rechercher(@Valid FactureCriteresDTO criteres) {
        return factureService.rechercher(criteres);
    }

    @GetMapping("/sous-types")
    public List<SousTypeDTO> sousTypes() {
        return factureService.sousTypes();
    }
}
