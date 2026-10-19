package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;

public interface PerimetreService {

    /** Perimetre de l'utilisateur de la requete en cours. */
    PerimetreDTO perimetreCourant();

    PerimetreDTO perimetre(Long utilisateurId);
}
