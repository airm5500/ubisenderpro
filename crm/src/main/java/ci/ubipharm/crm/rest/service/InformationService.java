package ci.ubipharm.crm.rest.service;

import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.InformationDTO;
import ci.ubipharm.crm.rest.service.dto.InformationDemandeDTO;

public interface InformationService {

    /** Informations actives (ou archivees), les plus recentes d'abord. */
    List<InformationDTO> lister(boolean archives);

    InformationDTO consulter(Long id);

    InformationDTO creer(InformationDemandeDTO demande);

    InformationDTO modifier(Long id, InformationDemandeDTO demande);

    /** Copie modifiable d'une information, pour annoncer une situation semblable. */
    InformationDTO dupliquer(Long id);

    InformationDTO archiver(Long id);

    InformationDTO reactiver(Long id);

    /** Refuse une information inconnue, archivee ou expiree. */
    void verifierCitable(Long id);

    /** Valeurs des variables d'information (source calcul.*), vides quand le champ n'est pas renseigne. */
    Map<String, String> valeursCampagne(Long id);
}
