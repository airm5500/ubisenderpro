package ci.ubipharm.crm.recouvrement.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.recouvrement.rest.service.VariableMessageService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.SourceVariableDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.VariableMessageDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.VariableMessageDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Variables des modeles : ajoutees par la Direction du Recouvrement sans developpement. */
@RestController
@RequestMapping("/api/recouvrement/variables")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class VariableMessageRessource {

    private static final String DIRECTION = "hasAuthority('MODULE_RECOUVREMENT') and hasAnyRole('ADMIN', 'DIR_RECOUVREMENT')";

    private final VariableMessageService variableMessageService;

    @GetMapping
    public List<VariableMessageDTO> variables() {
        return variableMessageService.variables();
    }

    @GetMapping("/sources")
    @PreAuthorize(DIRECTION)
    public List<SourceVariableDTO> sources() {
        return variableMessageService.sources();
    }

    @PostMapping
    @PreAuthorize(DIRECTION)
    public VariableMessageDTO creer(@Valid @RequestBody VariableMessageDemandeDTO demande) {
        return variableMessageService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(DIRECTION)
    public VariableMessageDTO modifier(@PathVariable Long id, @Valid @RequestBody VariableMessageDemandeDTO demande) {
        return variableMessageService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(DIRECTION)
    public VariableMessageDTO archiver(@PathVariable Long id) {
        return variableMessageService.archiver(id);
    }
}
