package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.EnvoiImmediatDTO;

/**
 * « Envoyer maintenant » sur un message en attente, quel que soit l'ecran (campagne, envoi en masse, historique) :
 * il part aussitot, hors plage horaire et sans attendre son tour.
 */
public interface EnvoiImmediatService {

    /** cle : C + id d'un message de campagne, S + id d'un message d'envoi en masse (celle de l'historique des envois). */
    EnvoiImmediatDTO envoyer(String cle);
}
