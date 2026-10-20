package ci.ubipharm.crm.rest.service;

/**
 * Envoi des relances des campagnes en cours, depuis le numero de chaque agent, a debit lent :
 * un message par numero et par passage, attente aleatoire entre deux messages, plage horaire et plafond par jour.
 */
public interface EnvoiWhatsappService {

    /** Un passage du planificateur ; renvoie le nombre de messages remis au service. */
    int envoyerLot();

    /**
     * Envoie aussitot ce message en attente, hors plage horaire, debit et plafond (demande d'Airman du 20/10/2026) ;
     * false s'il n'est plus en attente ou que sa campagne n'est pas en cours.
     */
    boolean envoyerMaintenant(Long messageId);

    /** Au demarrage : un message reste en cours d'envoi apres un arret brutal passe en echec, jamais renvoye en double. */
    int reprendreEnvoisInterrompus();
}
