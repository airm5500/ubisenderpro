package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.PerimetreDTO;

public interface PerimetreService {

    /** Perimetre de l'utilisateur de la requete en cours. */
    PerimetreDTO perimetreCourant();

    PerimetreDTO perimetre(Long utilisateurId);

    /**
     * Vrai quand la requete en cours vient du module Marketing d'un utilisateur habilite sur ce module :
     * le Marketing agit sur toutes les agences (decision d'Airman du 22/10/2026), le Recouvrement reste cloisonne.
     */
    boolean contexteMarketing();
}
