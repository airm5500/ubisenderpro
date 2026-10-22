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

import ci.ubipharm.crm.rest.service.SupportFaqService;
import ci.ubipharm.crm.rest.service.dto.SupportFaqDTO;
import ci.ubipharm.crm.rest.service.dto.SupportFaqDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** FAQ du centre de support : lue par tous, tenue par le titulaire du droit Support. */
@RestController
@RequestMapping("/api/support/faq")
@RequiredArgsConstructor
public class SupportFaqRessource {

    private static final String SUPPORT = "hasAuthority('DROIT_SUPPORT')";

    private final SupportFaqService supportFaqService;

    @GetMapping
    public List<SupportFaqDTO> lister() {
        return supportFaqService.lister(false);
    }

    @GetMapping("/archives")
    @PreAuthorize(SUPPORT)
    public List<SupportFaqDTO> archives() {
        return supportFaqService.lister(true);
    }

    @PostMapping
    @PreAuthorize(SUPPORT)
    public SupportFaqDTO creer(@Valid @RequestBody SupportFaqDemandeDTO demande) {
        return supportFaqService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SUPPORT)
    public SupportFaqDTO modifier(@PathVariable Long id, @Valid @RequestBody SupportFaqDemandeDTO demande) {
        return supportFaqService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(SUPPORT)
    public SupportFaqDTO archiver(@PathVariable Long id) {
        return supportFaqService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(SUPPORT)
    public SupportFaqDTO reactiver(@PathVariable Long id) {
        return supportFaqService.reactiver(id);
    }
}
