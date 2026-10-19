package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.AgenceDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceDemandeDTO;
import ci.ubipharm.crm.rest.service.dto.AgenceGestionDTO;

public interface AgenceService {

    /** Agences que l'utilisateur connecte a le droit de voir. */
    List<AgenceDTO> listerVisibles();

    /** Toutes les agences actives (ou archivees), pour leur administration. */
    List<AgenceGestionDTO> lister(boolean archives);

    AgenceGestionDTO creer(AgenceDemandeDTO demande);

    AgenceGestionDTO modifier(Long id, AgenceDemandeDTO demande);

    /** Refuse tant que des clients actifs ou des utilisateurs y sont rattaches. */
    AgenceGestionDTO archiver(Long id);

    AgenceGestionDTO reactiver(Long id);
}
