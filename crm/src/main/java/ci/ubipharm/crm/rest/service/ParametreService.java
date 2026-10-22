package ci.ubipharm.crm.rest.service;

import java.util.List;

import ci.ubipharm.crm.rest.service.dto.ParametreDTO;
import ci.ubipharm.crm.rest.service.dto.ParametreModificationDTO;

public interface ParametreService {

    /** Valeur du parametre, ou la valeur par defaut s'il est absent ou vide. */
    String valeur(String cle, String parDefaut);

    int entier(String cle, int parDefaut);

    /** Vrai si le parametre OUI_NON vaut OUI (ou, absent, selon la valeur par defaut). */
    default boolean oui(String cle, boolean parDefaut) {
        return "OUI".equals(valeur(cle, parDefaut ? "OUI" : "NON"));
    }

    /** Parametres modifiables, par categorie puis dans l'ordre d'affichage. */
    List<ParametreDTO> lister();

    /** Enregistre plusieurs valeurs d'un coup, toutes ou aucune ; chaque changement est journalise. */
    List<ParametreDTO> modifier(List<ParametreModificationDTO> modifications);
}
