package ci.ubipharm.crm.rest;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.AideService;
import ci.ubipharm.crm.rest.service.dto.AideDTO;
import ci.ubipharm.crm.rest.service.dto.AidePreferenceDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteCleDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Aide et guidage : lus par tout utilisateur connecte, textes reecrits par l'administrateur (droit Parametres). */
@RestController
@RequestMapping("/api/aide")
@RequiredArgsConstructor
public class AideRessource {

    private static final String PARAMETRES = "hasAuthority('DROIT_PARAMETRES')";

    private final AideService aideService;

    @GetMapping
    public AideDTO aide() {
        return aideService.aide();
    }

    @PutMapping("/preference")
    public AideDTO preference(@Valid @RequestBody AidePreferenceDTO preference) {
        return aideService.preference(preference.getActif());
    }

    @PutMapping("/textes")
    @PreAuthorize(PARAMETRES)
    public AideTexteDTO modifier(@Valid @RequestBody AideTexteDemandeDTO demande) {
        return aideService.modifier(demande);
    }

    @PostMapping("/textes/reinitialisation")
    @PreAuthorize(PARAMETRES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinitialiser(@Valid @RequestBody AideTexteCleDTO cle) {
        aideService.reinitialiser(cle);
    }
}
