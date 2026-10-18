package ci.ubipharm.crm.commun.rest.service;

import java.util.List;

import ci.ubipharm.crm.commun.rest.service.dto.ParametreDTO;
import ci.ubipharm.crm.commun.rest.service.dto.ParametreModificationDTO;

public interface ParametreService {

    /** Valeur du parametre, ou la valeur par defaut s'il est absent ou vide. */
    String valeur(String cle, String parDefaut);

    int entier(String cle, int parDefaut);

    /** Parametres modifiables, par categorie puis dans l'ordre d'affichage. */
    List<ParametreDTO> lister();

    /** Enregistre plusieurs valeurs d'un coup, toutes ou aucune ; chaque changement est journalise. */
    List<ParametreDTO> modifier(List<ParametreModificationDTO> modifications);
}
