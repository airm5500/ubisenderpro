package ci.ubipharm.crm.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.PortefeuilleService;
import ci.ubipharm.crm.rest.service.dto.AgentDTO;
import ci.ubipharm.crm.rest.service.dto.NumeroAgentDTO;
import ci.ubipharm.crm.rest.service.dto.PortefeuilleClientDTO;
import ci.ubipharm.crm.rest.service.dto.PortefeuilleCriteresDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recouvrement")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class PortefeuilleRessource {

    private final PortefeuilleService portefeuilleService;

    @GetMapping("/portefeuille")
    public PageDTO<PortefeuilleClientDTO> rechercher(@Valid PortefeuilleCriteresDTO criteres) {
        return portefeuilleService.rechercher(criteres);
    }

    @GetMapping("/agents")
    @PreAuthorize("hasAnyAuthority('DROIT_PORTEFEUILLES', 'DROIT_PERIMETRE_AGENCES')")
    public List<AgentDTO> agents(@RequestParam(required = false) Long agenceId) {
        return portefeuilleService.agents(agenceId);
    }

    @PutMapping("/agents/{agentId}/numero")
    @PreAuthorize("hasAuthority('DROIT_NUMEROS_TOUS')")
    public AgentDTO enregistrerNumero(@PathVariable Long agentId, @Valid @RequestBody NumeroAgentDTO numero) {
        return portefeuilleService.enregistrerNumero(agentId, numero);
    }
}
