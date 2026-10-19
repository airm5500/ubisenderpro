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

import ci.ubipharm.crm.rest.service.ModeleMessageService;
import ci.ubipharm.crm.rest.service.VariableMessageService;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.rest.service.dto.ModeleMessageDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.VariableMessageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Modeles promotionnels : lus par qui prepare les campagnes, ecrits par le Marketing. */
@RestController
@RequestMapping("/api/marketing")
@PreAuthorize("hasAnyAuthority('DROIT_MODELES_MARKETING', 'DROIT_CAMPAGNES_MARKETING')")
@RequiredArgsConstructor
public class ModeleMarketingRessource {

    private static final String MODULE = "MARKETING";
    private static final String REDACTEURS = "hasAuthority('DROIT_MODELES_MARKETING')";

    private final ModeleMessageService modeleMessageService;
    private final VariableMessageService variableMessageService;

    @GetMapping("/modeles")
    public List<ModeleMessageDTO> rechercher(ModeleMessageCriteresDTO criteres) {
        return modeleMessageService.rechercher(MODULE, criteres);
    }

    @GetMapping("/modeles/{id}")
    public ModeleMessageDTO consulter(@PathVariable Long id) {
        return modeleMessageService.consulter(MODULE, id);
    }

    @PostMapping("/modeles")
    @PreAuthorize(REDACTEURS)
    public ModeleMessageDTO creer(@Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.creer(MODULE, demande);
    }

    @PutMapping("/modeles/{id}")
    @PreAuthorize(REDACTEURS)
    public ModeleMessageDTO modifier(@PathVariable Long id, @Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.modifier(MODULE, id, demande);
    }

    @PostMapping("/modeles/{id}/archivage")
    @PreAuthorize(REDACTEURS)
    public ModeleMessageDTO archiver(@PathVariable Long id) {
        return modeleMessageService.archiver(MODULE, id);
    }

    @GetMapping("/variables")
    public List<VariableMessageDTO> variables() {
        return variableMessageService.variablesMarketing();
    }
}
