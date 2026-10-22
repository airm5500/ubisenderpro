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

import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.dto.ApercuListeDTO;
import ci.ubipharm.crm.rest.service.dto.ClientsListeDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MembresCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ValeursCriteresDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Listes de diffusion du Recouvrement (rubrique Relances), jamais visibles du Marketing ; le desabonnement reste au Marketing. */
@RestController
@RequestMapping("/api/recouvrement/listes")
@PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES')")
@RequiredArgsConstructor
public class ListeDiffusionRecouvrementRessource {

    private final ListeDiffusionService listeDiffusionService;

    @GetMapping
    public List<ListeDiffusionDTO> lister(@RequestParam(defaultValue = "false") boolean avecArchivees) {
        return listeDiffusionService.lister(avecArchivees);
    }

    @GetMapping("/{id}")
    public ListeDiffusionDTO consulter(@PathVariable Long id) {
        return listeDiffusionService.consulter(id);
    }

    @GetMapping("/{id}/membres")
    public ApercuListeDTO membres(@PathVariable Long id, @Valid MembresCriteresDTO criteres) {
        return listeDiffusionService.membres(id, criteres);
    }

    @GetMapping("/criteres")
    public ValeursCriteresDTO valeursCriteres() {
        return listeDiffusionService.valeursCriteres();
    }

    @PostMapping("/simulation")
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ApercuListeDTO simuler(@RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.simuler(demande);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ListeDiffusionDTO creer(@Valid @RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ListeDiffusionDTO modifier(@PathVariable Long id, @Valid @RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.modifier(id, demande);
    }

    @PostMapping("/{id}/clients")
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ListeDiffusionDTO changerClients(@PathVariable Long id, @Valid @RequestBody ClientsListeDTO demande) {
        return listeDiffusionService.changerClients(id, demande);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ListeDiffusionDTO archiver(@PathVariable Long id) {
        return listeDiffusionService.archiver(id);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize("hasAuthority('DROIT_LISTES_RELANCES_GERER')")
    public ListeDiffusionDTO reactiver(@PathVariable Long id) {
        return listeDiffusionService.reactiver(id);
    }
}
