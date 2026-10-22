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

import ci.ubipharm.crm.rest.service.DesabonnementService;
import ci.ubipharm.crm.rest.service.ListeDiffusionService;
import ci.ubipharm.crm.rest.service.dto.ApercuListeDTO;
import ci.ubipharm.crm.rest.service.dto.ClientsListeDTO;
import ci.ubipharm.crm.rest.service.dto.DesabonnementDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDTO;
import ci.ubipharm.crm.rest.service.dto.ListeDiffusionDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.MembresCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ValeursCriteresDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marketing")
@PreAuthorize("hasAuthority('DROIT_LISTES')")
@RequiredArgsConstructor
public class ListeDiffusionRessource {

    private final ListeDiffusionService listeDiffusionService;
    private final DesabonnementService desabonnementService;

    @GetMapping("/listes")
    public List<ListeDiffusionDTO> lister(@RequestParam(defaultValue = "false") boolean avecArchivees) {
        return listeDiffusionService.lister(avecArchivees);
    }

    @GetMapping("/listes/{id}")
    public ListeDiffusionDTO consulter(@PathVariable Long id) {
        return listeDiffusionService.consulter(id);
    }

    @GetMapping("/listes/{id}/membres")
    public ApercuListeDTO membres(@PathVariable Long id, @Valid MembresCriteresDTO criteres) {
        return listeDiffusionService.membres(id, criteres);
    }

    @GetMapping("/listes/criteres")
    public ValeursCriteresDTO valeursCriteres() {
        return listeDiffusionService.valeursCriteres();
    }

    @PostMapping("/listes/simulation")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ApercuListeDTO simuler(@RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.simuler(demande);
    }

    @PostMapping("/listes")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ListeDiffusionDTO creer(@Valid @RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.creer(demande);
    }

    @PutMapping("/listes/{id}")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ListeDiffusionDTO modifier(@PathVariable Long id, @Valid @RequestBody ListeDiffusionDemandeDTO demande) {
        return listeDiffusionService.modifier(id, demande);
    }

    @PostMapping("/listes/{id}/clients")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ListeDiffusionDTO changerClients(@PathVariable Long id, @Valid @RequestBody ClientsListeDTO demande) {
        return listeDiffusionService.changerClients(id, demande);
    }

    @PostMapping("/listes/{id}/archivage")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ListeDiffusionDTO archiver(@PathVariable Long id) {
        return listeDiffusionService.archiver(id);
    }

    @PostMapping("/listes/{id}/reactivation")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public ListeDiffusionDTO reactiver(@PathVariable Long id) {
        return listeDiffusionService.reactiver(id);
    }

    @PostMapping("/clients/{clientId}/desabonnement")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public void desabonner(@PathVariable Long clientId, @Valid @RequestBody(required = false) DesabonnementDTO demande) {
        desabonnementService.desabonner(clientId, demande == null ? null : demande.getMotif());
    }

    @PostMapping("/clients/{clientId}/reabonnement")
    @PreAuthorize("hasAuthority('DROIT_LISTES_GERER')")
    public void reabonner(@PathVariable Long clientId) {
        desabonnementService.reabonner(clientId);
    }
}
