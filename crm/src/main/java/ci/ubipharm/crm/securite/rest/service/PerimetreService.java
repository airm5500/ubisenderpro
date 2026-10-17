package ci.ubipharm.crm.securite.rest.service;

import ci.ubipharm.crm.securite.rest.service.dto.PerimetreDTO;

public interface PerimetreService {

    /** Perimetre de l'utilisateur de la requete en cours. */
    PerimetreDTO perimetreCourant();

    PerimetreDTO perimetre(Long utilisateurId);
}
