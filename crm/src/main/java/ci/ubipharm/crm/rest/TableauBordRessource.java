package ci.ubipharm.crm.rest;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.TableauBordService;
import ci.ubipharm.crm.rest.service.dto.EvolutionDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordDTO;
import ci.ubipharm.crm.rest.service.dto.TableauBordMarketingDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TableauBordRessource {

    private final TableauBordService tableauBordService;

    @GetMapping("/api/recouvrement/tableau-de-bord")
    @PreAuthorize("hasAuthority('DROIT_TABLEAU_BORD')")
    public TableauBordDTO recouvrement(@RequestParam(required = false) LocalDate du, @RequestParam(required = false) LocalDate au,
            @RequestParam(required = false) Long agenceId, @RequestParam(required = false) Long agentId) {
        return tableauBordService.recouvrement(du, au, agenceId, agentId);
    }

    @GetMapping("/api/marketing/tableau-de-bord")
    @PreAuthorize("hasAuthority('DROIT_TABLEAU_BORD_MARKETING')")
    public TableauBordMarketingDTO marketing(@RequestParam(required = false) LocalDate du, @RequestParam(required = false) LocalDate au) {
        return tableauBordService.marketing(du, au);
    }

    @GetMapping("/api/recouvrement/tableau-de-bord/evolution")
    @PreAuthorize("hasAuthority('DROIT_TABLEAU_BORD')")
    public EvolutionDTO evolutionRecouvrement(@RequestParam(required = false) LocalDate du, @RequestParam(required = false) LocalDate au,
            @RequestParam(required = false) Long agenceId, @RequestParam(required = false) Long agentId) {
        return tableauBordService.evolutionRecouvrement(du, au, agenceId, agentId);
    }

    @GetMapping("/api/marketing/tableau-de-bord/evolution")
    @PreAuthorize("hasAuthority('DROIT_TABLEAU_BORD_MARKETING')")
    public EvolutionDTO evolutionMarketing(@RequestParam(required = false) LocalDate du, @RequestParam(required = false) LocalDate au) {
        return tableauBordService.evolutionMarketing(du, au);
    }
}
