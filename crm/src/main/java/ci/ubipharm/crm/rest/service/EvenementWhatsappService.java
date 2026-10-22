package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.EvenementAccuseDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementMessageDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementStatutDTO;

/** Evenements remontes par le service wa-web, authentifies par le jeton partage. */
public interface EvenementWhatsappService {

    /** Refuse l'appel si le jeton est absent, faux, ou si aucun jeton n'est configure. */
    void verifierJeton(String jeton);

    void statut(EvenementStatutDTO evenement);

    /** Distribue puis lu ; un accuse ne fait jamais reculer un message ni sa facture. */
    void accuse(EvenementAccuseDTO evenement);

    /** Garde une copie de la reponse, rattachee au client et a sa derniere relance. */
    void message(EvenementMessageDTO evenement);

    /** Message refuse apres coup (API officielle) : il passe en echec avec le motif donne. */
    void echec(String numero, String identifiant, String motif);
}
