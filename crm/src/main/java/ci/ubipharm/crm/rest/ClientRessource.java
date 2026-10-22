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

import ci.ubipharm.crm.rest.service.ClientService;
import ci.ubipharm.crm.rest.service.MiseAJourSelectiveService;
import ci.ubipharm.crm.rest.service.VerificationNumeroService;
import ci.ubipharm.crm.rest.service.dto.DesactivationClientDTO;
import ci.ubipharm.crm.rest.service.dto.MiseAJourSelectiveDTO;
import ci.ubipharm.crm.rest.service.dto.ResultatMiseAJourDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientRessource {

    private final ClientService clientService;
    private final MiseAJourSelectiveService miseAJourSelectiveService;
    private final VerificationNumeroService verificationNumeroService;

    @GetMapping
    public PageDTO<ClientDTO> rechercher(@Valid ClientCriteresDTO criteres) {
        return clientService.rechercher(criteres);
    }

    @GetMapping("/{id}")
    public ClientDTO consulter(@PathVariable Long id) {
        return clientService.consulter(id);
    }

    @GetMapping("/segments")
    public List<SegmentDTO> segments() {
        return clientService.segments();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public ClientDTO creer(@Valid @RequestBody ClientDemandeDTO demande) {
        return clientService.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public ClientDTO modifier(@PathVariable Long id, @Valid @RequestBody ClientDemandeDTO demande) {
        return clientService.modifier(id, demande);
    }

    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public ClientDTO desactiver(@PathVariable Long id, @Valid @RequestBody DesactivationClientDTO demande) {
        return clientService.desactiver(id, demande.getMotif());
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public ClientDTO reactiver(@PathVariable Long id) {
        return clientService.reactiver(id);
    }

    @PostMapping("/mise-a-jour-selective")
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public ResultatMiseAJourDTO miseAJourSelective(@Valid @RequestBody MiseAJourSelectiveDTO demande) {
        return miseAJourSelectiveService.appliquer(demande);
    }

    @PostMapping("/verification-numeros")
    @PreAuthorize("hasAuthority('DROIT_CLIENTS_GERER')")
    public VerificationNumerosDTO verifierNumeros(@Valid @RequestBody VerificationNumerosDemandeDTO demande) {
        return verificationNumeroService.verifier(demande);
    }
}
