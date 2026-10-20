package ci.ubipharm.crm.rest;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.rest.service.SupportExperienceService;
import ci.ubipharm.crm.rest.service.dto.SupportExperienceDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMesureDTO;
import lombok.RequiredArgsConstructor;

/** Experience utilisateur : tout utilisateur connecte envoie ses mesures, le droit Support les consulte. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportExperienceRessource {

    private final SupportExperienceService supportExperienceService;

    /** Reponse {actif: false} : la mesure est desactivee, le navigateur cesse d'envoyer jusqu'a la prochaine connexion. */
    @PostMapping("/rum")
    public Map<String, Boolean> mesures(@RequestBody List<SupportMesureDTO> mesures) {
        return Map.of("actif", supportExperienceService.enregistrer(mesures));
    }

    @GetMapping("/experience")
    @PreAuthorize("hasAuthority('DROIT_SUPPORT')")
    public SupportExperienceDTO experience(@RequestParam(defaultValue = "24") int heures) {
        return supportExperienceService.consulter(heures);
    }
}
