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

import ci.ubipharm.crm.rest.service.SegmentService;
import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Segments de clients et leur badge : lus par les fiches clients, tenues par le titulaire du droit Referentiels. */
@RestController
@RequestMapping("/api/segments")
@RequiredArgsConstructor
public class SegmentRessource {

    private static final String GESTION = "hasAuthority('DROIT_REFERENTIELS')";

    private final SegmentService segmentService;

    @GetMapping
    public List<SegmentDTO> lister(@RequestParam(defaultValue = "false") boolean archives) {
        return segmentService.lister(archives);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    public SegmentDTO creer(@Valid @RequestBody SegmentDemandeDTO demande) {
        return segmentService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    public SegmentDTO modifier(@PathVariable Long id, @Valid @RequestBody SegmentDemandeDTO demande) {
        return segmentService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(GESTION)
    public SegmentDTO archiver(@PathVariable Long id) {
        return segmentService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize(GESTION)
    public SegmentDTO reactiver(@PathVariable Long id) {
        return segmentService.reactiver(id);
    }
}
