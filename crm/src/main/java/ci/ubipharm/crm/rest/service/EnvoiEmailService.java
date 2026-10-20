package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.EtatEmailDTO;

public interface EnvoiEmailService {

    /** Envoie le prochain e-mail de relance en attente, si l'heure, le plafond et le delai le permettent ; renvoie 0 ou 1. */
    int envoyerSuivant();

    /**
     * Envoie aussitot ce message en attente, hors plage horaire, debit et plafond (demande d'Airman du 20/10/2026) ;
     * false s'il n'est plus en attente ou que sa campagne n'est pas en cours.
     */
    boolean envoyerMaintenant(Long messageId);

    /** Au demarrage : un e-mail reste « en cours d'envoi » apres un arret brutal passe en echec, a verifier. */
    int reprendreEnvoisInterrompus();

    EtatEmailDTO etat();
}
