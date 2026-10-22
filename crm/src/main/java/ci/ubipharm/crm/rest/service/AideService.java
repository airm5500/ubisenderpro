package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.AideDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteCleDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDTO;
import ci.ubipharm.crm.rest.service.dto.AideTexteDemandeDTO;

public interface AideService {

    /** Interrupteurs du guidage et textes reecrits, pour l'utilisateur connecte. */
    AideDTO aide();

    /** Coupe ou reactive le guidage pour l'utilisateur connecte seulement. */
    AideDTO preference(boolean actif);

    /** Remplace le texte d'origine d'une infobulle ou d'un panneau d'aide. */
    AideTexteDTO modifier(AideTexteDemandeDTO demande);

    /** Revient au texte livre avec l'ecran. */
    void reinitialiser(AideTexteCleDTO cle);
}
