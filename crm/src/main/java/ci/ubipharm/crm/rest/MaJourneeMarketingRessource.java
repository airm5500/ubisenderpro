package ci.ubipharm.crm.rest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.MaJourneeMarketingService;
import ci.ubipharm.crm.rest.service.dto.MaJourneeMarketingDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MaJourneeMarketingRessource {

    private final MaJourneeMarketingService maJourneeMarketingService;

    @GetMapping("/api/marketing/ma-journee")
    @PreAuthorize("hasAnyAuthority('DROIT_CAMPAGNES_MARKETING', 'DROIT_PROMOTIONS', 'DROIT_EVENEMENTS', 'DROIT_TABLEAU_BORD_MARKETING')")
    public MaJourneeMarketingDTO maJournee() {
        return maJourneeMarketingService.maJournee();
    }
}
