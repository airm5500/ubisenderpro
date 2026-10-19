package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDTO;
import ci.ubipharm.crm.rest.service.dto.DelaiRelanceDemandeDTO;

public interface DelaiRelanceService {

    /** Delais par defaut (parametres campagne.*), sans agence ni segment. */
    DelaiRelanceDTO defauts();

    /** Regles visibles : celles de toutes les agences et celles des agences du perimetre. */
    List<DelaiRelanceDTO> lister(boolean archives);

    DelaiRelanceDTO creer(DelaiRelanceDemandeDTO demande);

    DelaiRelanceDTO modifier(Long id, DelaiRelanceDemandeDTO demande);

    DelaiRelanceDTO archiver(Long id);

    DelaiRelanceDTO reactiver(Long id);
}
