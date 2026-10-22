package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.MaJourneeMarketingDTO;

/** Page d'arrivee du Marketing ; chaque bloc n'est rempli que si l'utilisateur a le droit de le voir. */
public interface MaJourneeMarketingService {

    MaJourneeMarketingDTO maJournee();
}
