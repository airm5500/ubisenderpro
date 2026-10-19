package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.AgenceDTO;

public interface AgenceService {

    /** Agences que l'utilisateur connecte a le droit de voir. */
    List<AgenceDTO> listerVisibles();
}
