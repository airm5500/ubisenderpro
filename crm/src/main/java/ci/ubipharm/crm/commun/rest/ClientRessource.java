package ci.ubipharm.crm.commun.rest;

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
}
