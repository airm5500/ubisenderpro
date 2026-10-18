package ci.ubipharm.crm.commun.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import ci.ubipharm.crm.commun.rest.service.dto.ParametreDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ParametreModificationDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/parametres")
@PreAuthorize("hasAuthority('DROIT_PARAMETRES')")
@RequiredArgsConstructor
public class ParametreRessource {

    private final ParametreService parametreService;

    @GetMapping
    public List<ParametreDTO> lister() {
        return parametreService.lister();
    }

    @PutMapping
    public List<ParametreDTO> modifier(@RequestBody List<ParametreModificationDTO> modifications) {
        return parametreService.modifier(modifications);
    }
}
