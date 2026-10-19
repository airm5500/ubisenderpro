package ci.ubipharm.crm.rest.service;

/**
 * Envoi des relances des campagnes en cours, depuis le numero de chaque agent, a debit lent :
 * un message par numero et par passage, attente aleatoire entre deux messages, plage horaire et plafond par jour.
 */
public interface EnvoiWhatsappService {

    /** Un passage du planificateur ; renvoie le nombre de messages remis au service. */
    int envoyerLot();

    /** Au demarrage : un message reste en cours d'envoi apres un arret brutal passe en echec, jamais renvoye en double. */
    int reprendreEnvoisInterrompus();
}
