package ci.ubipharm.crm.rest.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import ci.ubipharm.crm.rest.service.dto.EvenementDTO;
import ci.ubipharm.crm.rest.service.dto.EvenementDemandeDTO;

public interface EvenementService {

    /** Evenements actifs (ou archives), du plus proche au plus lointain. */
    List<EvenementDTO> lister(boolean archives);

    EvenementDTO consulter(Long id);

    EvenementDTO creer(EvenementDemandeDTO demande);

    EvenementDTO modifier(Long id, EvenementDemandeDTO demande);

    EvenementDTO archiver(Long id);

    EvenementDTO reactiver(Long id);

    /** Refuse un evenement inconnu, archive ou deja passe. */
    void verifierCitable(Long id);

    /** Valeurs de {evenement_nom}, {evenement_date} et {evenement_fin} pour l'occurrence qui suit la date donnee. */
    Map<String, String> valeursCampagne(Long id, LocalDate reference);
}
