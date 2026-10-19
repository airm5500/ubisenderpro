package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.FactureService;
import ci.ubipharm.crm.rest.service.dto.FactureCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDTO;
import ci.ubipharm.crm.rest.service.dto.FactureDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SousTypeDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDTO;
import ci.ubipharm.crm.rest.service.dto.SuiviFactureDemandeDTO;
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

    @GetMapping("/factures/{id}")
    public FactureDTO consulter(@PathVariable Long id) {
        return factureService.consulter(id);
    }

    @PostMapping("/factures")
    @PreAuthorize("hasAuthority('DROIT_FACTURES_GERER')")
    public FactureDTO creer(@Valid @RequestBody FactureDemandeDTO demande) {
        return factureService.creer(demande);
    }

    @PutMapping("/factures/{id}")
    @PreAuthorize("hasAuthority('DROIT_FACTURES_GERER')")
    public FactureDTO modifier(@PathVariable Long id, @Valid @RequestBody FactureDemandeDTO demande) {
        return factureService.modifier(id, demande);
    }

    @PostMapping("/factures/{id}/suivi")
    @PreAuthorize("hasAnyAuthority('DROIT_FACTURES_SUIVI', 'DROIT_FACTURES_CONTENTIEUX')")
    public FactureDTO poserSuivi(@PathVariable Long id, @Valid @RequestBody SuiviFactureDemandeDTO demande) {
        return factureService.poserSuivi(id, demande);
    }

    @GetMapping("/factures/{id}/suivis")
    public List<SuiviFactureDTO> historiqueSuivi(@PathVariable Long id) {
        return factureService.historiqueSuivi(id);
    }

    @GetMapping("/sous-types")
    public List<SousTypeDTO> sousTypes() {
        return factureService.sousTypes();
    }
}
