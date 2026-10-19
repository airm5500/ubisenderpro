package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.UtilisateurPrincipal;

public interface UtilisateurCourantService {

    /** Utilisateur de la requete en cours ; refuse l'acces si personne n'est connecte. */
    UtilisateurPrincipal utilisateur();
}
