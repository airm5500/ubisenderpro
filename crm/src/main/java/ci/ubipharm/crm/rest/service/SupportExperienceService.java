package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.SupportExperienceDTO;
import ci.ubipharm.crm.rest.service.dto.SupportMesureDTO;

/**
 * Experience utilisateur (RUM) : le navigateur mesure le temps d'ouverture des ecrans et la duree des appels, les agrege et les envoie
 * chaque minute ; le serveur les range par tranche de 5 minutes, en une seule ecriture par envoi.
 */
public interface SupportExperienceService {

    /** Enregistre les mesures de l'utilisateur connecte ; renvoie false si la mesure est desactivee (le navigateur cesse d'en envoyer). */
    boolean enregistrer(List<SupportMesureDTO> mesures);

    /** Synthese des heures dernieres heures (1 a 336). */
    SupportExperienceDTO consulter(int heures);

    /** Retire les mesures plus anciennes que support.rum_retention_jours ; renvoie leur nombre. */
    int purger();
}
