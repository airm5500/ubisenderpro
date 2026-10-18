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

import ci.ubipharm.crm.recouvrement.rest.service.ModeleMessageService;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageCriteresDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDTO;
import ci.ubipharm.crm.recouvrement.rest.service.dto.ModeleMessageDemandeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Modeles de messages : lus par tout le module, ecrits par la Direction du Recouvrement. */
@RestController
@RequestMapping("/api/recouvrement/modeles-messages")
@PreAuthorize("hasAuthority('MODULE_RECOUVREMENT')")
@RequiredArgsConstructor
public class ModeleMessageRessource {

    private static final String DIRECTION = "hasAuthority('DROIT_MODELES_GERER')";

    private final ModeleMessageService modeleMessageService;

    @GetMapping
    public List<ModeleMessageDTO> rechercher(ModeleMessageCriteresDTO criteres) {
        return modeleMessageService.rechercher(criteres);
    }

    @GetMapping("/{id}")
    public ModeleMessageDTO consulter(@PathVariable Long id) {
        return modeleMessageService.consulter(id);
    }

    @PostMapping
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO creer(@Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO modifier(@PathVariable Long id, @Valid @RequestBody ModeleMessageDemandeDTO demande) {
        return modeleMessageService.modifier(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize(DIRECTION)
    public ModeleMessageDTO archiver(@PathVariable Long id) {
        return modeleMessageService.archiver(id);
    }
}
