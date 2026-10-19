package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ClientCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDTO;
import ci.ubipharm.crm.rest.service.dto.ClientDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.SegmentDTO;
import ci.ubipharm.crm.socle.dto.PageDTO;

public interface ClientService {

    PageDTO<ClientDTO> rechercher(ClientCriteresDTO criteres);

    ClientDTO consulter(Long id);

    /** Nouveau client saisi a la main, dans une agence de son perimetre. */
    ClientDTO creer(ClientDemandeDTO demande);

    /** Une valeur videe est effacee ; un numero remplace ou retire est archive, jamais supprime. */
    ClientDTO modifier(Long id, ClientDemandeDTO demande);

    List<SegmentDTO> segments();
}
