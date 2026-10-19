package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.DelegationCriteresDTO;
import ci.ubipharm.crm.rest.service.dto.DelegationDTO;
import ci.ubipharm.crm.rest.service.dto.DelegationDemandeDTO;

public interface DelegationService {

    DelegationDTO creer(DelegationDemandeDTO demande);

    List<DelegationDTO> rechercher(DelegationCriteresDTO criteres);

    /** Met fin a la delegation des maintenant ; elle reste dans l'historique. */
    DelegationDTO annuler(Long id);
}
