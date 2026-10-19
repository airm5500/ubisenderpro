package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.EtatEmailDTO;

public interface EnvoiEmailService {

    /** Envoie le prochain e-mail de relance en attente, si l'heure, le plafond et le delai le permettent ; renvoie 0 ou 1. */
    int envoyerSuivant();

    /** Au demarrage : un e-mail reste « en cours d'envoi » apres un arret brutal passe en echec, a verifier. */
    int reprendreEnvoisInterrompus();

    EtatEmailDTO etat();
}
