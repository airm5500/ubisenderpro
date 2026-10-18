package ci.ubipharm.crm.commun.rest;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import ci.ubipharm.crm.commun.rest.service.dto.ClientDemandeDTO;
import ci.ubipharm.crm.commun.rest.service.dto.SegmentDTO;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ci.ubipharm.crm.commun.rest.service.ClientService;
import ci.ubipharm.crm.commun.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientRessource {

    private final ClientService clientService;

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
}
