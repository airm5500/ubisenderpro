package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.AnalyseCampagneDTO;

public interface AnalyseCampagneService {

    /** Chiffres et courbe d'une campagne, Recouvrement ou Marketing ; l'acces est verifie par le service du module avant l'appel. */
    AnalyseCampagneDTO analyser(Long campagneId);
}
