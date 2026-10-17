package ci.ubipharm.crm.commun.rest.service;

import ci.ubipharm.crm.commun.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface ClientService {

    PageDTO<ClientDTO> rechercher(ClientCriteresDTO criteres);

    ClientDTO consulter(Long id);
}
