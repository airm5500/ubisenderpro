package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.CompteMetaDTO;
import ci.ubipharm.crm.rest.service.dto.CompteMetaDemandeDTO;

public interface CompteMetaService {

    CompteMetaDTO consulter();

    CompteMetaDTO modifier(CompteMetaDemandeDTO demande);

    /** Nouveau jeton de verification du webhook, a recopier chez Meta. */
    CompteMetaDTO renouvelerJetonVerification();
}
